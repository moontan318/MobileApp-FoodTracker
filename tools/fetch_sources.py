#!/usr/bin/env python3
"""Downloads the UK source data used to build the bundled food database.

Run by .github/workflows/fetch-data.yml (GitHub's runners have open internet access),
followed by tools/build_food_db.py. Writes to tools/sources/:
  cofid/*.csv   the CoFID 2021 sheets used by the build (Open Government Licence v3.0)
  raw/*.gz      menu nutrition published by restaurant chains on their own websites
                (Greggs, Costa, LEON, Nando's, Popeyes, Burger King, McDonald's, KFC, Subway)

McDonald's, KFC and Subway block scripted downloads, so those are fetched through a
headless Firefox (Playwright) session, the same way a visitor's browser loads them.
"""
import asyncio
import json
import urllib.parse
import csv
import gzip
import re
import urllib.request
from pathlib import Path

OUT = Path(__file__).resolve().parent / "sources"
UA = {"User-Agent": "Mozilla/5.0 (MealMacros data build; +https://github.com/moontan318/MobileApp-FoodTracker)",
      "Accept": "application/json, text/html;q=0.9, */*;q=0.8"}

COFID_PAGE = "https://www.gov.uk/api/content/government/publications/composition-of-foods-integrated-dataset-cofid"
COFID_SHEETS = {"1_1_Notes", "1_3_Proximates", "1_4_Inorganics", "1_5_Vitamins"}

BK_QUERY = ('*[_type == "item" && defined(nutrition.calories)]'
            '{_id, name, nutrition, "sections": *[_type == "section" && references(^._id)].name}')

RESTAURANTS = {
    "greggs.json": "https://production-digital.greggs.co.uk/api/v1.0/articles/masters"
                   "?ExcludeUnpublished=true&ExcludeDuplicates=true&ExcludeHiddenFromMenu=true",
    "costa_menu.html": "https://www.costa.co.uk/menu",
    "leon_menu.html": "https://leon.co/menu/",
    "nandos.json": "https://www.nandos.co.uk/food/menu/page-data/index/page-data.json",
    "popeyes.json": "https://app-allergenplatform-prod-westeurope-001.azurewebsites.net/api/recipes/get-information"
                    "?pagination%5Bpage%5D=0&pagination%5BpageSize%5D=1000&allergens=false&nutrition=true",
    "burgerking.json": "https://czqk28jt.apicdn.sanity.io/v2021-10-21/data/query/prod_bk_gb?query="
                       + urllib.parse.quote(BK_QUERY),
}

MCD = "https://www.mcdonalds.com"
KFC_PDF = "https://brand-uk.assets.kfc.co.uk/nutrition-allergens.pdf"
SUBWAY_PAGE = "https://www.subway.com/en-GB/MenuNutrition/Nutrition"


def get(url, timeout=120, headers=None):
    with urllib.request.urlopen(urllib.request.Request(url, headers={**UA, **(headers or {})}), timeout=timeout) as r:
        return r.read()


def save_raw(name, data):
    dest = OUT / "raw"
    dest.mkdir(parents=True, exist_ok=True)
    (dest / f"{name}.gz").write_bytes(gzip.compress(data, 9, mtime=0))
    print(len(data), name)


def fetch_cofid():
    import openpyxl  # installed by the workflow
    page = get(COFID_PAGE).decode("utf-8")
    urls = sorted(set(re.findall(r'https://assets\.publishing\.service\.gov\.uk/[^"\s<>]+?\.xlsx', page)))
    url = next(u for u in urls if "Integrated_Dataset" in u)
    print("CoFID:", url)
    dest = OUT / "cofid"
    dest.mkdir(parents=True, exist_ok=True)
    (dest / "SOURCES.txt").write_text(url + "\n")
    tmp = dest / "download.xlsx"
    tmp.write_bytes(get(url, timeout=300))
    stem = re.sub(r"[^A-Za-z0-9]+", "_", Path(url).stem)[:60]
    workbook = openpyxl.load_workbook(tmp, read_only=True, data_only=True)
    for ws in workbook.worksheets:
        name = re.sub(r"[^A-Za-z0-9]+", "_", ws.title).strip("_")
        if name not in COFID_SHEETS:
            continue
        with open(dest / f"{stem}__{name}.csv", "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            for row in ws.iter_rows(values_only=True):
                writer.writerow(["" if v is None else v for v in row])
    tmp.unlink()


def attempt(name, fn):
    """Runs one download; on failure the previously fetched file is kept."""
    try:
        return fn()
    except Exception as e:  # noqa: BLE001
        print(f"FAILED {name}: {type(e).__name__}: {e}")
        return None


async def attempt_async(name, coro):
    try:
        return await coro
    except Exception as e:  # noqa: BLE001
        print(f"FAILED {name}: {type(e).__name__}: {e}")
        return None


def fetch_restaurants():
    for name, url in RESTAURANTS.items():
        headers = {"Origin": "https://popeyesuk.com", "Referer": "https://popeyesuk.com/"} if "popeyes" in name else None
        attempt(name, lambda: save_raw(name, get(url, headers=headers)))


async def fetch_with_browser():
    from playwright.async_api import async_playwright  # installed by the workflow
    async with async_playwright() as p:
        browser = await p.firefox.launch()
        context = await browser.new_context(locale="en-GB")
        page = await context.new_page()
        await attempt_async("mcdonalds", fetch_mcdonalds(page, context))
        await attempt_async("kfc", fetch_kfc(page, context))
        await attempt_async("subway", fetch_subway(page, context))
        await browser.close()


async def fetch_mcdonalds(page, context):
    """Crawls the menu and product pages for product ids, then fetches each product's details."""
    queue = [f"{MCD}/gb/en-gb/full-menu.html", f"{MCD}/gb/en-gb/menu.html",
             f"{MCD}/gb/en-gb/product/big-mac.html"]
    seen, ids = set(), set()
    while queue and len(seen) < 250:
        url = queue.pop(0)
        if url in seen:
            continue
        seen.add(url)
        try:
            await page.goto(url, wait_until="domcontentloaded", timeout=45_000)
            await page.wait_for_timeout(2000)
            html = await page.content()
        except Exception as e:  # noqa: BLE001
            print(f"McDonald's: {url}: {type(e).__name__}")
            continue
        ids |= set(re.findall(r'data-product-id="(\d+)"', html))
        for path in re.findall(r'(/gb/en-gb/(?:full-menu|menu|product)/[a-z0-9\-/]+\.html)', html):
            if MCD + path not in seen:
                queue.append(MCD + path)
    print(f"McDonald's: {len(seen)} pages, {len(ids)} products")
    items = []
    for item_id in sorted(ids):
        url = f"{MCD}/dnaapp/itemDetails?country=UK&language=en&showLiveData=true&item={item_id}"
        resp = await context.request.get(url)
        if resp.ok:
            try:
                items.append((await resp.json())["item"])
            except Exception:  # noqa: BLE001 - skip malformed entries
                pass
        await page.wait_for_timeout(300)
    if len(items) < 20:
        raise RuntimeError(f"only {len(items)} McDonald's items")
    save_raw("mcdonalds.json", json.dumps(items).encode())


async def fetch_kfc(page, context):
    await page.goto("https://www.kfc.co.uk/nutrition-allergens", wait_until="domcontentloaded", timeout=60_000)
    resp = await context.request.get(KFC_PDF)
    body = await resp.body()
    if not body.startswith(b"%PDF"):
        raise RuntimeError(f"KFC: not a PDF (HTTP {resp.status})")
    save_raw("kfc_nutrition.pdf", body)


async def fetch_subway(page, context):
    await page.goto(SUBWAY_PAGE, wait_until="domcontentloaded", timeout=60_000)
    await page.wait_for_timeout(4000)
    pdfs = re.findall(r'https://media\.subway\.com/[^"\'\s<>]+?\.pdf', await page.content())
    nutrition_pdf = next(u for u in pdfs if "Nutrition" in u)
    print("Subway:", nutrition_pdf)
    resp = await context.request.get(nutrition_pdf)
    body = await resp.body()
    if not body.startswith(b"%PDF"):
        raise RuntimeError(f"Subway: not a PDF (HTTP {resp.status})")
    save_raw("subway_nutrition.pdf", body)


if __name__ == "__main__":
    fetch_cofid()
    fetch_restaurants()
    asyncio.run(fetch_with_browser())
