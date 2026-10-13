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
    steps = sys.argv[1:] or ["cofid", "probe"]
    if "cofid" in steps:
        fetch_cofid()
    if "probe" in steps:
        probe()
