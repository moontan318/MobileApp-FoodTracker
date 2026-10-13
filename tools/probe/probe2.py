#!/usr/bin/env python3
"""Second investigation round (see probe_restaurants.py)."""
import asyncio
import gzip
import json
import re
import urllib.parse
import urllib.request
from pathlib import Path

from playwright.async_api import async_playwright

OUT = Path(__file__).resolve().parent / "out2"
OUT.mkdir(parents=True, exist_ok=True)
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"


def save(name, data):
    (OUT / f"{name}.gz").write_bytes(gzip.compress(data if isinstance(data, bytes) else data.encode(), 9, mtime=0))


def get(url, headers=None):
    req = urllib.request.Request(url, headers={"User-Agent": UA, **(headers or {})})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.status, r.read()


def apis():
    log = []
    groq = {
        "bk_count": 'count(*[defined(nutrition.calories)])',
        "bk_types": 'array::unique(*[defined(nutrition.calories)]._type)',
        "bk_items": '*[defined(nutrition.calories)]{_id, _type, name, nutrition, "sections": *[_type=="section" && references(^._id)].name}',
    }
    for name, q in groq.items():
        url = ("https://czqk28jt.apicdn.sanity.io/v2021-10-21/data/query/prod_bk_gb?query="
               + urllib.parse.quote(q))
        try:
            status, body = get(url)
            save(name + ".json", body)
            log.append(f"{name}: {status} {len(body)} {body[:300]!r}")
        except Exception as e:  # noqa: BLE001
            log.append(f"{name}: ERROR {e}")
    try:
        status, body = get("https://app-allergenplatform-prod-westeurope-001.azurewebsites.net/api/recipes/"
                           "get-information?pagination%5Bpage%5D=0&pagination%5BpageSize%5D=1000&allergens=false&nutrition=true",
                           {"Origin": "https://popeyesuk.com", "Referer": "https://popeyesuk.com/"})
        save("popeyes.json", body)
        log.append(f"popeyes: {status} {len(body)}")
    except Exception as e:  # noqa: BLE001
        log.append(f"popeyes: ERROR {e}")
    return log


BLOCKED = {
    "kfc": ["https://www.kfc.co.uk/nutrition-allergens", "https://www.kfc.co.uk/menu"],
    "subway": ["https://www.subway.com/en-GB/MenuNutrition/Nutrition"],
    "pizzahut": ["https://www.pizzahut.co.uk/menu/"],
    "mcdonalds": ["https://www.mcdonalds.com/gb/en-gb/product/big-mac.html"],
    "dominos": ["https://www.dominos.co.uk/menu"],
    "papajohns": ["https://www.papajohns.co.uk/menu"],
}
KCAL = re.compile(rb"(?i)kcal|calorie")


async def browsers():
    log = []
    async with async_playwright() as p:
        engines = [("firefox", p.firefox, {}), ("webkit", p.webkit, {}),
                   ("chromium_h1", p.chromium, {"args": ["--disable-http2", "--disable-blink-features=AutomationControlled"]})]
        for ename, engine, opts in engines:
            try:
                browser = await engine.launch(**opts)
            except Exception as e:  # noqa: BLE001
                log.append(f"{ename}: launch failed {e}")
                continue
            for chain, urls in BLOCKED.items():
                ctx = await browser.new_context(locale="en-GB", viewport={"width": 1366, "height": 900})
                page = await ctx.new_page()
                saved = []

                async def on_resp(resp, chain=chain, ename=ename, saved=saved):
                    try:
                        ct = resp.headers.get("content-type", "")
                        if "json" not in ct and "pdf" not in ct:
                            return
                        body = await resp.body()
                        if len(body) > 300 and ("pdf" in ct or KCAL.search(body)):
                            n = f"{chain}_{ename}_r{len(saved):02d}"
                            save(n + (".pdf" if "pdf" in ct else ".json"), body)
                            saved.append(f"{n} {len(body)} {resp.url[:300]}")
                    except Exception:  # noqa: BLE001
                        pass

                page.on("response", lambda r, f=on_resp: asyncio.ensure_future(f(r)))
                for url in urls:
                    try:
                        resp = await page.goto(url, wait_until="domcontentloaded", timeout=45_000)
                        await page.wait_for_timeout(8000)
                        for _ in range(5):
                            await page.mouse.wheel(0, 2500)
                            await page.wait_for_timeout(700)
                        html = await page.content()
                        save(f"{chain}_{ename}_page.html", html)
                        log.append(f"{ename} {chain} {url}: status={resp.status if resp else None} "
                                   f"html={len(html)} kcal={len(re.findall('(?i)kcal', html))}")
                    except Exception as e:  # noqa: BLE001
                        log.append(f"{ename} {chain} {url}: ERROR {str(e)[:200]}")
                await page.wait_for_timeout(1500)
                log.extend("   " + s for s in saved)
                await ctx.close()
            await browser.close()
    return log


if __name__ == "__main__":
    lines = apis() + asyncio.run(browsers())
    (OUT / "summary.txt").write_text("\n".join(lines) + "\n")
    print("\n".join(lines))
