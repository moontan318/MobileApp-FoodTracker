#!/usr/bin/env python3
"""One-off investigation: loads UK restaurant nutrition pages in a real (headless) browser
and records any nutrition data the pages fetch, so parsers can be written for them.

Output: tools/probe/out/<chain>/ with summary.txt, page.html.gz and every JSON/PDF response
that mentions calories.
"""
import asyncio
import gzip
import json
import re
from pathlib import Path

from playwright.async_api import async_playwright

OUT = Path(__file__).resolve().parent / "out"

PAGES = {
    "mcdonalds": ["https://www.mcdonalds.com/gb/en-gb/menu.html",
                  "https://www.mcdonalds.com/gb/en-gb/product/big-mac.html"],
    "kfc": ["https://www.kfc.co.uk/nutrition-allergens", "https://www.kfc.co.uk/menu"],
    "burgerking": ["https://www.burgerking.co.uk/menu", "https://www.burgerking.co.uk/nutrition-explorer"],
    "subway": ["https://www.subway.com/en-GB/MenuNutrition/Nutrition", "https://www.subway.com/en-GB/MenuNutrition/Menu"],
    "dominos": ["https://www.dominos.co.uk/nutritional-information", "https://www.dominos.co.uk/menu"],
    "pizzahut": ["https://www.pizzahut.co.uk/order/nutrition/", "https://www.pizzahut.co.uk/menu/"],
    "nandos": ["https://www.nandos.co.uk/food/menu", "https://www.nandos.co.uk/food/nutrition"],
    "fiveguys": ["https://fiveguys.co.uk/nutrition", "https://fiveguys.co.uk/menu/"],
    "papajohns": ["https://www.papajohns.co.uk/nutritional-information.aspx", "https://www.papajohns.co.uk/menu"],
    "tacobell": ["https://www.tacobell.co.uk/nutrition", "https://www.tacobell.co.uk/menu"],
    "popeyes": ["https://popeyesuk.com/nutrition", "https://popeyesuk.com/menu"],
    "wendys": ["https://www.wendys.co.uk/menu", "https://www.wendys.co.uk/nutrition"],
    "wimpy": ["https://wimpy.uk.com/menu/"],
    "german_doner": ["https://www.germandonerkebab.com/uk/menu/"],
    "chopstix": ["https://chopstixnoodlebar.com/menu/"],
    "starbucks": ["https://www.starbucks.co.uk/menu"],
    "pret": ["https://www.pret.co.uk/en-GB/products/categories/sandwiches"],
    "wagamama": ["https://www.wagamama.com/menu"],
    "itsu": ["https://www.itsu.com/menu/"],
    "tortilla": ["https://www.tortilla.co.uk/menu/"],
    "wasabi": ["https://wasabi.uk.com/menu"],
    "wetherspoon": ["https://www.jdwetherspoon.com/food"],
    "caffenero": ["https://caffenero.com/uk/menu/"],
    "harvester": ["https://www.harvester.co.uk/menus"],
}

KCAL = re.compile(rb"(?i)kcal|calorie|energy")


async def probe(browser, chain, urls):
    dest = OUT / chain
    dest.mkdir(parents=True, exist_ok=True)
    context = await browser.new_context(
        user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                   "Chrome/126.0 Safari/537.36",
        locale="en-GB", viewport={"width": 1366, "height": 900})
    page = await context.new_page()
    saved, lines = [], []

    async def on_response(resp):
        try:
            ctype = resp.headers.get("content-type", "")
            if not any(t in ctype for t in ("json", "pdf", "javascript", "text/plain")):
                return
            body = await resp.body()
            if len(body) < 200 or len(body) > 30_000_000:
                return
            if "pdf" in ctype or KCAL.search(body[:2_000_000]):
                if "javascript" in ctype and not re.search(rb"(?i)kcal", body):
                    return
                n = len(saved)
                ext = "pdf" if "pdf" in ctype else ("js" if "javascript" in ctype else "json")
                (dest / f"resp{n:03d}.{ext}.gz").write_bytes(gzip.compress(body, 9, mtime=0))
                saved.append(f"resp{n:03d}.{ext}\t{len(body)}\t{resp.status}\t{resp.url[:400]}")
        except Exception:  # noqa: BLE001 - best effort
            pass

    page.on("response", lambda r: asyncio.ensure_future(on_response(r)))
    for i, url in enumerate(urls):
        try:
            resp = await page.goto(url, wait_until="domcontentloaded", timeout=45_000)
            await page.wait_for_timeout(6000)
            for _ in range(6):  # scroll to trigger lazy loading
                await page.mouse.wheel(0, 2500)
                await page.wait_for_timeout(800)
            html = await page.content()
            (dest / f"page{i}.html.gz").write_bytes(gzip.compress(html.encode(), 9, mtime=0))
            links = sorted(set(re.findall(r'https?://[^"\'\s<>]+', html)))
            hits = [l for l in links if re.search(r"(?i)nutri|allergen|\.pdf", l)]
            lines.append(f"{url}\nstatus={resp.status if resp else None} html={len(html)} "
                         f"kcal_in_html={len(re.findall('(?i)kcal', html))}\n  " + "\n  ".join(hits[:80]))
        except Exception as e:  # noqa: BLE001
            lines.append(f"{url}\nERROR {type(e).__name__}: {e}"[:600])
    await page.wait_for_timeout(2000)
    await context.close()
    (dest / "summary.txt").write_text("\n\n".join(lines) + "\n\n== responses\n" + "\n".join(saved) + "\n")
    print(chain, len(saved), "responses")


async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch()
        for chain, urls in PAGES.items():
            await probe(browser, chain, urls)
        await browser.close()


if __name__ == "__main__":
    asyncio.run(main())
