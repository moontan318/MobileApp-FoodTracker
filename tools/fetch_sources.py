#!/usr/bin/env python3
"""Downloads the UK source data used to build the bundled food database.

Run by .github/workflows/fetch-data.yml (GitHub's runners have open internet access).
Writes to tools/sources/:
  cofid/*.csv          every sheet of McCance & Widdowson's CoFID (Open Government Licence)
  probes/*.txt         HTTP status + start of the response for candidate restaurant sources
"""
import csv
import json
import re
import sys
import urllib.request
from pathlib import Path

OUT = Path(__file__).resolve().parent / "sources"
UA = {"User-Agent": "Mozilla/5.0 (MealMacros data build; +https://github.com/moontan318/MobileApp-FoodTracker)",
      "Accept": "application/json, text/html;q=0.9, */*;q=0.8"}


def get(url, binary=False, timeout=60):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        data = r.read()
        return r.status, (data if binary else data.decode("utf-8", "replace"))


def fetch_cofid():
    import openpyxl  # installed by the workflow
    api = "https://www.gov.uk/api/content/government/publications/composition-of-foods-integrated-dataset-cofid"
    _, text = get(api)
    urls = sorted(set(re.findall(r'https://assets\.publishing\.service\.gov\.uk/[^"\s<>]+?\.xlsx?', text)))
    print("CoFID attachments:", *urls, sep="\n  ")
    dest = OUT / "cofid"
    dest.mkdir(parents=True, exist_ok=True)
    (dest / "SOURCES.txt").write_text("\n".join(urls) + "\n")
    for url in urls:
        _, data = get(url, binary=True, timeout=300)
        tmp = dest / "download.xlsx"
        tmp.write_bytes(data)
        wb = openpyxl.load_workbook(tmp, read_only=True, data_only=True)
        stem = re.sub(r"[^A-Za-z0-9]+", "_", Path(url).stem)[:60]
        for ws in wb.worksheets:
            name = re.sub(r"[^A-Za-z0-9]+", "_", ws.title).strip("_")
            with open(dest / f"{stem}__{name}.csv", "w", newline="", encoding="utf-8") as f:
                w = csv.writer(f)
                for row in ws.iter_rows(values_only=True):
                    w.writerow(["" if v is None else v for v in row])
        tmp.unlink()


# Second-round probes: full pages are searched for nutrition data and links.
DEEP_RAW = {
    "greggs.json": "https://production-digital.greggs.co.uk/api/v1.0/articles/masters?ExcludeUnpublished=true&ExcludeDuplicates=true&ExcludeHiddenFromMenu=true",
    "costa_menu.html": "https://www.costa.co.uk/menu",
    "leon_menu.html": "https://leon.co/menu/",
}


def fetch_raw():
    import gzip
    dest = OUT / "raw"
    dest.mkdir(parents=True, exist_ok=True)
    for name, url in RAW.items():
        status, data = get(url, binary=True, timeout=120)
        (dest / f"{name}.gz").write_bytes(gzip.compress(data, 9))
        print(status, len(data), name)


PROBES = {
    "nandos": "https://www.nandos.co.uk/food/menu",
    "wagamama": "https://www.wagamama.com/menu",
    "costa": "https://www.costa.co.uk/menu",
    "pret": "https://www.pret.co.uk/en-GB/nutrition",
    "subway": "https://www.subway.com/en-GB/MenuNutrition/Nutrition",
    "burgerking": "https://www.burgerking.co.uk/menu",
    "kfc": "https://www.kfc.co.uk/nutrition-allergens",
    "kfc_home": "https://www.kfc.co.uk/",
    "mcdonalds_nutrition": "https://www.mcdonalds.com/gb/en-gb/good-to-know/nutrition-calculator.html",
    "pizzahut": "https://www.pizzahut.co.uk/menu/",
    "pizzaexpress": "https://www.pizzaexpress.com/menu",
    "fiveguys": "https://fiveguys.co.uk/menu/",
    "leon": "https://leon.co/menu/",
    "itsu": "https://www.itsu.com/menu/",
    "starbucks": "https://www.starbucks.co.uk/menu",
    "caffenero": "https://caffenero.com/uk/menu/",
    "wetherspoon": "https://www.jdwetherspoon.com/food",
    "tacobell": "https://www.tacobell.co.uk/menu",
    "popeyes": "https://popeyesuk.com/menu",
    "tortilla": "https://www.tortilla.co.uk/menu/",
    "wasabi": "https://wasabi.uk.com/menu",
    "zizzi": "https://www.zizzi.co.uk/menu",
    "toby": "https://www.tobycarvery.co.uk/menu",
    "harvester": "https://www.harvester.co.uk/menus",
    "chopstix": "https://www.chopstixnoodlebar.com/menu",
    "papajohns": "https://www.papajohns.co.uk/nutritional-information",
}


def deep_probe():
    dest = OUT / "probes_deep"
    dest.mkdir(parents=True, exist_ok=True)
    for name, url in DEEP_PROBES.items():
        try:
            status, text = get(url, timeout=60)
        except Exception as e:  # noqa: BLE001
            status, text = getattr(e, "code", "ERR"), f"{type(e).__name__}: {e}"
        kcal = [m.start() for m in re.finditer(r"(?i)kcal|calories", text)]
        snippets = [text[max(0, i - 300): i + 200].replace("\n", " ") for i in kcal[:15]]
        links = sorted(set(re.findall(r'https?://[^"\'\s<>()]+', text)))
        interesting = [l for l in links if re.search(r"(?i)nutri|allergen|\.pdf|/api/|menu.*json|graphql", l)]
        report = [url, f"status: {status}", f"length: {len(text)}", f"kcal mentions: {len(kcal)}",
                  f"next_data: {'__NEXT_DATA__' in text}", "", "== interesting links", *interesting[:200],
                  "", "== kcal snippets", *snippets, "", "== head", text[:3000]]
        (dest / f"{name}.txt").write_text("\n".join(map(str, report)))
        print(f"{status}\t{len(text)}\tkcal={len(kcal)}\tlinks={len(interesting)}\t{name}")


RAW = {
    "greggs.json": "https://production-digital.greggs.co.uk/api/v1.0/articles/masters?ExcludeUnpublished=true&ExcludeDuplicates=true&ExcludeHiddenFromMenu=true",
    "costa_menu.html": "https://www.costa.co.uk/menu",
    "leon_menu.html": "https://leon.co/menu/",
}


def fetch_raw():
    import gzip
    dest = OUT / "raw"
    dest.mkdir(parents=True, exist_ok=True)
    for name, url in RAW.items():
        status, data = get(url, binary=True, timeout=120)
        (dest / f"{name}.gz").write_bytes(gzip.compress(data, 9))
        print(status, len(data), name)


PROBES = {
    "off_uk_mcdonalds": "https://world.openfoodfacts.org/api/v2/search?countries_tags_en=united-kingdom&brands_tags=mcdonald-s&fields=code,product_name,brands,nutriments,serving_size&page_size=20",
    "off_uk_popular": "https://world.openfoodfacts.org/api/v2/search?countries_tags_en=united-kingdom&sort_by=unique_scans_n&fields=code,product_name,brands,nutriments,serving_size&page_size=5",
    "mcd_itemlist": "https://www.mcdonalds.com/dnaapp/itemList?country=UK&language=en&showLiveData=true",
    "mcd_full_menu": "https://www.mcdonalds.com/gb/en-gb/full-menu.html",
    "greggs_api": "https://production-digital.greggs.co.uk/api/v1.0/articles/masters?ExcludeUnpublished=true&ExcludeDuplicates=true&ExcludeHiddenFromMenu=true",
    "greggs_menu": "https://www.greggs.com/menu",
    "kfc_nutrition": "https://www.kfc.co.uk/nutrition-allergens",
    "nandos_menu": "https://www.nandos.co.uk/food/menu",
    "burgerking_uk": "https://www.burgerking.co.uk/menu",
    "subway_uk": "https://www.subway.com/en-GB/MenuNutrition/Nutrition",
    "pret_uk": "https://www.pret.co.uk/en-GB/nutrition",
    "wagamama": "https://www.wagamama.com/menu",
    "dominos_uk": "https://www.dominos.co.uk/nutritional-information",
    "costa_uk": "https://www.costa.co.uk/menu",
    "fsa_ndns": "https://www.gov.uk/api/content/government/collections/national-diet-and-nutrition-survey",
}


def probe():
    dest = OUT / "probes"
    dest.mkdir(parents=True, exist_ok=True)
    for name, url in PROBES.items():
        try:
            status, text = get(url, timeout=40)
        except Exception as e:  # noqa: BLE001 - record any failure
            status, text = getattr(e, "code", "ERR"), f"{type(e).__name__}: {e}"
        (dest / f"{name}.txt").write_text(f"{url}\nstatus: {status}\nlength: {len(text)}\n\n{text[:20000]}")
        print(f"{status}\t{len(text)}\t{name}")


if __name__ == "__main__":
    steps = sys.argv[1:] or ["raw"]
    if "cofid" in steps:
        fetch_cofid()
    if "probe" in steps:
        probe()
    if "deep" in steps:
        deep_probe()
    if "raw" in steps:
        fetch_raw()
