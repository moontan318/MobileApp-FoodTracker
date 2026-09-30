#!/usr/bin/env python3
"""Convert USDA SR28 (ABBREV.txt, FOOD_DES.txt, WEIGHT.txt) into the compact TSVs bundled with the app.

Source data: USDA National Nutrient Database for Standard Reference, Release 28
(public domain). The raw files can be obtained from the USDA, from the
`fda-nutrient-database` npm package (ABBREV.txt, FOOD_DES.txt) and from
github.com/alyssaq/usda-sqlite (data/WEIGHT.txt).

Usage: python3 tools/build_food_db.py <dir with the SR28 files> app/src/main/assets
Writes usda_sr28.tsv (nutrients per 100 g) and usda_portions.tsv (household measures).
"""
import sys
from pathlib import Path

# Nutrient key -> column index in ABBREV.txt (see SR28 documentation, p. 44).
COLUMNS = {
    "energy": 3, "protein": 4, "fat": 5, "carbs": 7, "fiber": 8, "sugars": 9,
    "saturated_fat": 44, "mono_fat": 45, "poly_fat": 46, "cholesterol": 47, "water": 2,
    "vitamin_a": 33, "vitamin_c": 20, "vitamin_d": 41, "vitamin_e": 40, "vitamin_k": 43,
    "thiamin": 21, "riboflavin": 22, "niacin": 23, "pantothenic_acid": 24, "vitamin_b6": 25,
    "folate": 29, "vitamin_b12": 31, "choline": 30,
    "calcium": 10, "iron": 11, "magnesium": 12, "phosphorus": 13, "potassium": 14,
    "sodium": 15, "zinc": 16, "copper": 17, "manganese": 18, "selenium": 19,
}

GROUPS = {
    "0100": "Dairy & Egg", "0200": "Spices & Herbs", "0300": "Baby Foods", "0400": "Fats & Oils",
    "0500": "Poultry", "0600": "Soups & Sauces", "0700": "Sausages & Cold Meats",
    "0800": "Breakfast Cereals", "0900": "Fruits", "1000": "Pork", "1100": "Vegetables",
    "1200": "Nuts & Seeds", "1300": "Beef", "1400": "Beverages", "1500": "Fish & Shellfish",
    "1600": "Legumes", "1700": "Lamb, Veal & Game", "1800": "Baked Products", "1900": "Sweets",
    "2000": "Grains & Pasta", "2100": "Fast Foods", "2200": "Meals & Entrees", "2500": "Snacks",
    "3500": "Indigenous Foods", "3600": "Restaurant Foods",
}


def fields(line):
    return [f.strip("~") for f in line.rstrip("\r\n").split("^")]


def main(src, dest_dir):
    src = Path(src)
    dest_dir = Path(dest_dir)
    desc = {}
    for line in (src / "FOOD_DES.txt").read_text(encoding="latin-1").splitlines():
        f = fields(line)
        desc[f[0]] = (f[2], f[1], f[4])  # long description, group, common name

    keys = list(COLUMNS)
    out = ["\t".join(["id", "name", "group", "common"] + keys)]
    for line in (src / "ABBREV.txt").read_text(encoding="latin-1").splitlines():
        f = fields(line)
        name, group, common = desc[f[0]]
        values = [f[COLUMNS[k]] for k in keys]
        out.append("\t".join([f[0], name, GROUPS.get(group, ""), common] + values))

    (dest_dir / "usda_sr28.tsv").write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"wrote {len(out) - 1} foods")

    # WEIGHT.txt: id, seq, amount, description, grams. Stored as grams for ONE unit.
    portions = ["\t".join(["id", "description", "grams"])]
    for line in (src / "WEIGHT.txt").read_text(encoding="latin-1").splitlines():
        f = fields(line)
        amount, grams = float(f[2] or 0), float(f[4] or 0)
        if f[0] not in desc or amount <= 0 or grams <= 0:
            continue
        portions.append("\t".join([f[0], f[3].strip(), f"{grams / amount:.4g}"]))
    (dest_dir / "usda_portions.tsv").write_text("\n".join(portions) + "\n", encoding="utf-8")
    print(f"wrote {len(portions) - 1} portions")


if __name__ == "__main__":
    main(*sys.argv[1:3])
