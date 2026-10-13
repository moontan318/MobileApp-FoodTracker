#!/usr/bin/env python3
"""Downloads the UK source data used to build the bundled food database.

Run by .github/workflows/fetch-data.yml (GitHub's runners have open internet access),
followed by tools/build_food_db.py. Writes to tools/sources/:
  cofid/*.csv   the CoFID 2021 sheets used by the build (Open Government Licence v3.0)
  raw/*.gz      menu nutrition published by Greggs, Costa and LEON on their websites
"""
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

RESTAURANTS = {
    "greggs.json": "https://production-digital.greggs.co.uk/api/v1.0/articles/masters"
                   "?ExcludeUnpublished=true&ExcludeDuplicates=true&ExcludeHiddenFromMenu=true",
    "costa_menu.html": "https://www.costa.co.uk/menu",
    "leon_menu.html": "https://leon.co/menu/",
}


def get(url, timeout=120):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=timeout) as r:
        return r.read()


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


def fetch_restaurants():
    dest = OUT / "raw"
    dest.mkdir(parents=True, exist_ok=True)
    for name, url in RESTAURANTS.items():
        data = get(url)
        (dest / f"{name}.gz").write_bytes(gzip.compress(data, 9, mtime=0))
        print(len(data), name)


if __name__ == "__main__":
    fetch_cofid()
    fetch_restaurants()
