#!/usr/bin/env python3
"""Builds the food database bundled with the app (app/src/main/assets/foods.tsv + portions.tsv).

Sources (all under tools/sources/; cofid/ and raw/ are refreshed by tools/fetch_sources.py in CI):
  cofid/  McCance & Widdowson's Composition of Foods Integrated Dataset 2021 (UK, Open
          Government Licence v3.0). The primary database, with UK food names.
  usda/   USDA SR28 (public domain), from tools/convert_sr28.py. Renamed to UK wording;
          US-branded and US-restaurant items are dropped.
  raw/    Menu nutrition published by restaurant chains on their own websites (Greggs, Costa,
          LEON, McDonald's, KFC, Burger King, Subway, Nando's, Popeyes), and the most popular
          UK supermarket products from Open Food Facts (off_uk.tsv.gz, Open Database Licence).

Requires pdfplumber (pip install pdfplumber) for the KFC and Subway PDFs.

Usage: python3 -I tools/build_food_db.py
"""
import csv
import gzip
import html
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "sources"
ASSETS = ROOT.parent / "app" / "src" / "main" / "assets"

NUTRIENTS = [
    "energy", "protein", "fat", "carbs", "fiber", "sugars", "saturated_fat", "mono_fat", "poly_fat",
    "cholesterol", "water", "vitamin_a", "vitamin_c", "vitamin_d", "vitamin_e", "vitamin_k", "thiamin",
    "riboflavin", "niacin", "pantothenic_acid", "vitamin_b6", "folate", "vitamin_b12", "choline",
    "calcium", "iron", "magnesium", "phosphorus", "potassium", "sodium", "zinc", "copper", "manganese",
    "selenium",
]

RESTAURANT_GROUP = "Restaurant & takeaway"


def fmt(v):
    return "" if v is None else f"{v:.4g}"


# --------------------------------------------------------------------------------------------
# CoFID (UK)
# --------------------------------------------------------------------------------------------

COFID_PREFIX = "McCance_Widdowsons_Composition_of_Foods_Integrated_Dataset_2__"

# nutrient key -> (sheet, CoFID column codes in order of preference)
COFID_COLUMNS = {
    "energy": ("1_3_Proximates", ["KCALS"]),
    "protein": ("1_3_Proximates", ["PROT"]),
    "fat": ("1_3_Proximates", ["FAT"]),
    "carbs": ("1_3_Proximates", ["CHO"]),
    "fiber": ("1_3_Proximates", ["AOACFIB", "ENGFIB"]),
    "sugars": ("1_3_Proximates", ["TOTSUG"]),
    "saturated_fat": ("1_3_Proximates", ["SATFOD"]),
    "mono_fat": ("1_3_Proximates", ["MONOFOD", "MONOFODc"]),
    "poly_fat": ("1_3_Proximates", ["POLYFOD", "POLYFODc"]),
    "cholesterol": ("1_3_Proximates", ["CHOL"]),
    "water": ("1_3_Proximates", ["WATER"]),
    "vitamin_a": ("1_5_Vitamins", ["RETEQU"]),
    "vitamin_c": ("1_5_Vitamins", ["VITC"]),
    "vitamin_d": ("1_5_Vitamins", ["VITD"]),
    "vitamin_e": ("1_5_Vitamins", ["VITE"]),
    "vitamin_k": ("1_5_Vitamins", ["VITK1"]),
    "thiamin": ("1_5_Vitamins", ["THIA"]),
    "riboflavin": ("1_5_Vitamins", ["RIBO"]),
    "niacin": ("1_5_Vitamins", ["NIAC"]),
    "pantothenic_acid": ("1_5_Vitamins", ["PANTO"]),
    "vitamin_b6": ("1_5_Vitamins", ["VITB6"]),
    "folate": ("1_5_Vitamins", ["FOLT"]),
    "vitamin_b12": ("1_5_Vitamins", ["VITB12"]),
    "calcium": ("1_4_Inorganics", ["CA"]),
    "iron": ("1_4_Inorganics", ["FE"]),
    "magnesium": ("1_4_Inorganics", ["MG"]),
    "phosphorus": ("1_4_Inorganics", ["P"]),
    "potassium": ("1_4_Inorganics", ["K"]),
    "sodium": ("1_4_Inorganics", ["NA"]),
    "zinc": ("1_4_Inorganics", ["ZN"]),
    "copper": ("1_4_Inorganics", ["CU"]),
    "manganese": ("1_4_Inorganics", ["MN"]),
    "selenium": ("1_4_Inorganics", ["SE"]),
}

# CoFID group code -> display group (longest matching prefix wins)
COFID_GROUPS = {
    "A": "Cereals & bakery", "AB": "Sandwiches", "AC": "Rice", "AD": "Pasta", "AE": "Pizza",
    "AF": "Bread", "AG": "Bread", "AI": "Breakfast cereals", "AM": "Biscuits", "AN": "Cakes",
    "AP": "Buns & pastries", "AS": "Puddings & desserts",
    "B": "Dairy", "BJ": "Cream", "BL": "Cheese", "BN": "Yoghurt", "BP": "Ice cream",
    "BR": "Puddings & desserts", "C": "Eggs",
    "D": "Vegetables", "DA": "Potatoes", "DB": "Beans & pulses", "DR": "Vegetable dishes",
    "F": "Fruit", "FC": "Juices", "PE": "Juices", "G": "Nuts & seeds", "H": "Herbs & spices",
    "J": "Fish & seafood", "M": "Meat", "MBG": "Burgers", "MI": "Meat products", "MIG": "Meat dishes",
    "MR": "Meat dishes", "O": "Fats & oils", "P": "Drinks", "Q": "Alcoholic drinks",
    "S": "Sugars & sweets", "SN": "Snacks", "W": "Soups & sauces",
}


def cofid_group(code, name):
    if "takeaway" in name.lower() or "restaurant" in name.lower():
        return "Takeaway"
    for n in range(len(code), 0, -1):
        if code[:n] in COFID_GROUPS:
            return COFID_GROUPS[code[:n]]
    return ""


def cofid_value(text):
    text = text.strip()
    if text == "Tr":
        return 0.0  # trace
    if text in ("", "N"):
        return None  # not measured
    return float(text)


def load_cofid():
    sheets = {}
    for sheet in {s for s, _ in COFID_COLUMNS.values()}:
        rows = list(csv.reader(open(SRC / "cofid" / f"{COFID_PREFIX}{sheet}.csv", encoding="utf-8")))
        sheets[sheet] = (rows[1], {r[0].strip(): r for r in rows[3:] if r[0].strip()})
    foods = []
    for code, row in sheets["1_3_Proximates"][1].items():
        name = " ".join(row[1].split())
        values = {}
        for key, (sheet, cols) in COFID_COLUMNS.items():
            header, data = sheets[sheet]
            for col in cols:
                v = cofid_value(data[code][header.index(col)])
                if v is not None:
                    values[key] = v
                    break
        foods.append({"id": f"cofid:{code}", "name": name, "group": cofid_group(row[3].strip(), name),
                      "source": "UK", "common": "", "values": values})
    return foods


# --------------------------------------------------------------------------------------------
# USDA SR28, renamed to UK English
# --------------------------------------------------------------------------------------------

# Applied in order. US wording stays searchable through the "common" column.
UK_NAMES = [(re.compile(p), r) for p, r in [
    (r"^Biscuits, ", "Scones (American biscuits), "),
    (r"\bbiscuits\b", "scones"),
    (r"^Cookies, ", "Biscuits, "),
    (r"\bcookies?\b", "biscuits"),
    (r"^Snacks, potato chips", "Snacks, crisps"),
    (r"\bpotato chips\b", "crisps"),
    (r"\bfrench fried\b", "chips (french fries)"),
    (r"\bfrench fries\b", "chips (french fries)"),
    (r"^Candies, ", "Sweets, "),
    (r"\bcandies\b", "sweets"),
    (r"^(Beef|Pork|Lamb|Turkey|Chicken|Veal), ground\b", r"\1 mince"),
    (r"\bground (beef|pork|lamb|turkey|chicken)\b", r"\1 mince"),
    (r"^Eggplant\b", "Aubergine"),
    (r"\beggplant\b", "aubergine"),
    (r"^Squash, summer, zucchini", "Courgette"),
    (r"\bzucchini\b", "courgette"),
    (r"^Coriander \(cilantro\) leaves", "Coriander leaves"),
    (r"\bcilantro\b", "coriander leaves"),
    (r"^Arugula\b", "Rocket"),
    (r"\barugula\b", "rocket"),
    (r"^Rutabagas\b", "Swede"),
    (r"\brutabagas?\b", "swede"),
    (r"\bscallions\b", "spring onions"),
    (r"^Peas, edible-podded", "Mangetout (edible-podded peas)"),
    (r"\bsnow peas\b", "mangetout"),
    (r"^Chickpeas \(garbanzo beans, bengal gram\)", "Chickpeas"),
    (r"\bgarbanzo\b", "chickpea"),
    (r"^Beans, lima\b", "Beans, butter (lima)"),
    (r"\blima beans\b", "butter beans"),
    (r"^Beans, navy\b", "Beans, haricot (navy)"),
    (r"^Beets\b", "Beetroot"),
    (r"\bbeets\b", "beetroot"),
    (r"^Peppers, sweet\b", "Peppers"),
    (r"^Peppers, hot chili", "Chillies, hot"),
    (r"\bchili\b", "chilli"),
    (r"^Corn, sweet,", "Sweetcorn,"),
    (r"^Cornstarch\b", "Cornflour"),
    (r"\bcornstarch\b", "cornflour"),
    (r"^Crustaceans, shrimp", "Prawns"),
    (r"\bshrimp\b", "prawns"),
    (r"^Catsup\b", "Ketchup"),
    (r"\bcatsup\b", "ketchup"),
    (r"^Gelatin desserts\b", "Jelly (dessert)"),
    (r"^Jellies\b", "Jelly (fruit spread)"),
    (r"\bbroiled\b", "grilled"),
    (r"\bbroilers? or fryers, ", ""),
    (r"\bconfectioners'?", "icing"),
    (r"\bpowdered sugar\b", "icing sugar"),
    (r"\ball-purpose\b", "plain"),
    (r"\bself-rising\b", "self-raising"),
    (r"\bheavy whipping\b", "double / whipping"),
    (r"^Molasses\b", "Black treacle (molasses)"),
    (r"\bmolasses\b", "black treacle (molasses)"),
    (r"\bromaine\b", "cos (romaine)"),
    (r"\bfrankfurter\b", "frankfurter (hot dog sausage)"),
    (r"\bfiber\b", "fibre"),
    (r"\bflavor\b", "flavour"),
    (r"\bflavored\b", "flavoured"),
    (r"\bcolor\b", "colour"),
    (r"\b([Yy])ogurt\b", r"\1oghurt"),
    (r"\bcenter\b", "centre"),
    (r"\bcanola\b", "rapeseed (canola)"),
    (r"^Beverages, carbonated", "Fizzy drinks"),
    (r"\bcarbonated beverage\b", "fizzy drink"),
]]

USDA_GROUPS = {
    "Sweets": "Sugars & sweets", "Fast Foods": "Fast food (US)", "Baked Products": "Cereals & bakery",
    "Sausages & Cold Meats": "Meat products", "Grains & Pasta": "Cereals & bakery",
    "Fish & Shellfish": "Fish & seafood", "Spices & Herbs": "Herbs & spices", "Dairy & Egg": "Dairy",
    "Legumes": "Beans & pulses", "Lamb, Veal & Game": "Meat", "Beef": "Meat", "Pork": "Meat",
    "Poultry": "Meat", "Meals & Entrees": "Meals", "Fruits": "Fruit",
}

# All-caps words are US brand names (e.g. KELLOGG'S); these generic ones are not.
NOT_BRANDS = {"USDA", "NFS", "DHA", "ARA", "EPA", "UHT", "NLEA", "WIC", "II", "III"}
BRAND = re.compile(r"\b[A-Z][A-Z'&.\-]{2,}\b")


def uk_name(name):
    for pattern, repl in UK_NAMES:
        name = pattern.sub(repl, name)
    return name[:1].upper() + name[1:]


def load_usda():
    rows = list(csv.reader(open(SRC / "usda" / "usda_sr28.tsv", encoding="utf-8"), delimiter="\t"))
    header = rows[0]
    foods = []
    for r in rows[1:]:
        f = dict(zip(header, r))
        if f["group"] in ("Restaurant Foods", "Indigenous Foods"):
            continue
        if any(w not in NOT_BRANDS for w in BRAND.findall(f["name"])):
            continue
        name = uk_name(f["name"])
        common = f["common"] if name == f["name"] else f"{f['common']} {f['name']}".strip()
        foods.append({"id": f"usda:{f['id']}", "name": name, "group": USDA_GROUPS.get(f["group"], f["group"]),
                      "source": "US", "common": common,
                      "values": {k: float(f[k]) for k in NUTRIENTS if f.get(k)}})
    return foods


def load_usda_portions():
    portions = {}
    for line in (SRC / "usda" / "usda_portions.tsv").read_text(encoding="utf-8").splitlines()[1:]:
        ndb, desc, grams = line.split("\t")
        portions.setdefault(ndb, []).append((desc, float(grams)))
    return portions


# --------------------------------------------------------------------------------------------
# Restaurants
# --------------------------------------------------------------------------------------------

def label_values(label, weight):
    """Converts per-portion label values (kcal, fat, saturates, carbs, sugars, fibre, protein,
    salt) to nutrients per 100 g."""
    keys = {"kcal": "energy", "fat": "fat", "saturates": "saturated_fat", "carbs": "carbs",
            "sugars": "sugars", "fibre": "fiber", "protein": "protein"}
    values = {keys[k]: v * 100.0 / weight for k, v in label.items() if k in keys and v is not None}
    if label.get("salt") is not None:
        values["sodium"] = label["salt"] * 400.0 * 100.0 / weight  # salt (g) -> sodium (mg)
    return values


def raw(name):
    return gzip.decompress((SRC / "raw" / name).read_bytes()).decode("utf-8")


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def restaurant_food(fid, name, source, values, per="100g"):
    """per="item": the chain doesn't publish weights, so values are per item."""
    # "McDonald's McDonald's Fries" -> "McDonald's Fries"
    name = re.sub(rf"^{re.escape(source)}\s+(?={re.escape(source)}\b)", "", name, flags=re.IGNORECASE)
    return {"id": fid, "name": name, "group": RESTAURANT_GROUP, "source": source, "common": "",
            "values": values, "per": per}


def item_values(label):
    """Label values for one item, kept per item (for chains that don't publish weights)."""
    return label_values(label, 100.0)  # weight 100 leaves the numbers unchanged


def has_raw(name):
    if (SRC / "raw" / name).exists():
        return True
    print(f"warning: tools/sources/raw/{name} missing, skipped")
    return False


def load_greggs():
    foods, portions = [], {}
    for a in json.loads(raw("greggs.json.gz")):
        size = (a.get("articleSize") or {}).get("size")
        nv = {n["name"]: n["value"] for n in a.get("nutritionalValues") or []}
        if not size or nv.get("Calories") is None:
            continue
        fid = f"greggs:{a['articleCode']}"
        name = " ".join(a["articleName"].replace("®", "").split())
        foods.append(restaurant_food(fid, f"Greggs {name}", "Greggs", label_values({
            "kcal": nv.get("Calories"), "fat": nv.get("Fat"), "saturates": nv.get("Saturates"),
            "carbs": nv.get("Carbohydrates"), "sugars": nv.get("Sugar"), "protein": nv.get("Protein"),
            "salt": nv.get("Salt"),
        }, size)))
        portions[fid] = [("item", size)]
    return foods, portions


def astro_decode(x):
    """Costa's page embeds its menu in Astro's serialisation: [0, value] or [1, [items]]."""
    if isinstance(x, list) and len(x) == 2 and isinstance(x[0], int):
        t, v = x
        return [astro_decode(i) for i in v] if t == 1 else astro_decode(v)
    if isinstance(x, dict):
        return {k: astro_decode(v) for k, v in x.items()}
    return x


COSTA_DEFAULT_MILK = {None, "NA", "Semi-skimmed"}
COSTA_SIZE_ORDER = ["Small", "Medium", "Large"]


def load_costa():
    props = html.unescape(re.search(r'props="([^"]*)"', raw("costa_menu.html.gz")).group(1))
    catalog = astro_decode(json.loads(props))["catalog"]
    foods, portions, seen = [], {}, set()
    for section in catalog.values():
        for group in section:
            for p in group.get("products") or []:
                if p["id"] in seen:
                    continue
                seen.add(p["id"])
                by_milk = {}  # milk type -> size -> (preference, per 100 g, per portion)
                for n in p.get("nutrition") or []:
                    var = n.get("variations") or {}
                    per100g, portion = n.get("nutritionPer100g"), n.get("nutritionPerPortion")
                    if not per100g or not portion or not portion.get("portionWeight"):
                        continue
                    if per100g.get("energykCal") is None or var.get("coffeeType") not in (None, "NA", "Signature"):
                        continue  # skip incomplete records and decaf / alternative roasts
                    pref = {"Takeaway": 0, "Eat In": 1}.get(var.get("serviceDelivery"), 2)
                    sizes = by_milk.setdefault(var.get("milkType"), {})
                    size = var.get("size")
                    if size not in sizes or pref < sizes[size][0]:
                        sizes[size] = (pref, per100g, portion)
                for milk, sizes in by_milk.items():
                    order = sorted(sizes, key=lambda s: COSTA_SIZE_ORDER.index(s) if s in COSTA_SIZE_ORDER else -1)
                    v = (sizes.get("Medium") or sizes[order[0]])[1]
                    if milk in COSTA_DEFAULT_MILK:
                        suffix = ""
                    elif milk == "None":
                        suffix = ", no milk"
                    else:
                        suffix = f", {milk.lower()} milk"
                    fid = f"costa:{slug(p['name'] + suffix)}"
                    name = f"Costa {' '.join(p['name'].split())}{suffix}"
                    values = {k: v.get(src) for k, src in [
                        ("energy", "energykCal"), ("fat", "fat"), ("saturated_fat", "saturates"),
                        ("carbs", "carbohydrates"), ("sugars", "sugars"), ("fiber", "fibre"),
                        ("protein", "protein"), ("vitamin_b6", "vitaminB6"), ("vitamin_b12", "vitaminB12"),
                        ("vitamin_c", "vitaminC"), ("zinc", "zinc")]}
                    if v.get("salt") is not None:
                        values["sodium"] = v["salt"] * 400.0
                    foods.append(restaurant_food(fid, name, "Costa", {k: x for k, x in values.items() if x is not None}))
                    portions[fid] = [(s if s in COSTA_SIZE_ORDER else "item", sizes[s][2]["portionWeight"])
                                     for s in order]
    return foods, portions


def load_leon():
    page = raw("leon_menu.html.gz")
    foods, portions, seen = [], {}, set()
    for chunk in re.findall(r'self\.__next_f\.push\(\[1,("(?:[^"\\]|\\.)*")\]\)', page):
        text = json.loads(chunk)
        for m in re.finditer(r'"name":"((?:[^"\\]|\\.)*)","nutritionInfo":(\{[^{}]*\})', text):
            name = " ".join(json.loads(f'"{m.group(1)}"').split())
            info = json.loads(m.group(2))
            weight = info.get("totalPortionWeight")
            if not weight or info.get("kcal") is None or name.lower() in seen:
                continue
            seen.add(name.lower())
            fid = f"leon:{slug(name)}"
            foods.append(restaurant_food(fid, f"LEON {name}", "LEON", label_values({
                "kcal": info.get("kcal"), "fat": info.get("fat"), "saturates": info.get("satFat"),
                "carbs": info.get("carb"), "sugars": info.get("sugar"), "fibre": info.get("fibre"),
                "protein": info.get("protein"), "salt": info.get("salt"),
            }, weight)))
            portions[fid] = [("item", weight)]
    return foods, portions


def load_popeyes():
    foods, portions, seen = [], {}, set()
    if not has_raw("popeyes.json.gz"):
        return foods, portions
    for r in json.loads(raw("popeyes.json.gz"))["data"]:
        n = r.get("nutrition") or {}
        name = " ".join(r["name"].split())
        if n.get("kcal") is None or name.lower() in seen or "ireland only" in name.lower():
            continue
        seen.add(name.lower())
        fid = f"popeyes:{r['id']}"
        foods.append(restaurant_food(fid, f"Popeyes {name}", "Popeyes", item_values({
            "kcal": n.get("kcal"), "fat": n.get("fats"), "saturates": n.get("saturated_fats"),
            "carbs": n.get("carbohydrates"), "sugars": n.get("sugar"), "fibre": n.get("fibre"),
            "protein": n.get("protein"), "salt": n.get("salt"),
        }), per="item"))
        portions[fid] = [("item", 100.0)]
    return foods, portions


def nandos_values(facts):
    mg = lambda k: None if facts.get(k) is None else facts[k] / 1000.0  # noqa: E731 - values are in mg
    return item_values({
        "kcal": facts.get("energyKcal"), "fat": mg("fatMg"), "saturates": mg("saturatesMg"),
        "carbs": mg("totalCarbsMg"), "sugars": mg("sugarsMg"), "fibre": mg("fibreMg"),
        "protein": mg("proteinMg"), "salt": mg("saltMg"),
    })


def load_nandos():
    foods, portions, seen = [], {}, set()
    if not has_raw("nandos.json.gz"):
        return foods, portions
    menu = json.loads(raw("nandos.json.gz"))["result"]["data"]["nandos"]["menu"]

    def add(name, facts, size=None):
        name = " ".join(name.split())
        full = f"{name} ({size})" if size else name
        if slug(full) in seen or facts.get("energyKcal") is None:
            return
        seen.add(slug(full))
        fid = f"nandos:{slug(full)}"
        foods.append(restaurant_food(fid, f"Nando's {full}", "Nando's", nandos_values(facts), per="item"))
        portions[fid] = [("item", 100.0)]

    for section in menu["sections"]:
        for item in section["items"]:
            facts = (item.get("nutritionalInfo") or {}).get("factsForPortionSizes") or []
            sizes = None
            if len(facts) > 1:  # e.g. Regular / Large: size names come from the item's options
                for m in item.get("modifiers") or []:
                    names = [o.get("displayName") for o in m.get("options") or []]
                    if len(names) == len(facts) and all(names):
                        sizes = names
                        break
            for i, f in enumerate(facts):
                add(item["displayName"], f, sizes[i] if sizes else (None if len(facts) == 1 else f"size {i + 1}"))
            for m in item.get("modifiers") or []:  # sides, dips and extras offered with the dish
                for o in m.get("options") or []:
                    of = (o.get("nutritionalInfo") or {}).get("factsForPortionSizes") or []
                    if len(of) == 1 and o.get("displayName"):
                        add(o["displayName"], of[0])
    return foods, portions


def pdf_tables(name):
    import io
    import pdfplumber  # pip install pdfplumber
    with pdfplumber.open(io.BytesIO(gzip.decompress((SRC / "raw" / name).read_bytes()))) as pdf:
        return [t for page in pdf.pages for t in page.extract_tables()]


def number(text):
    text = (text or "").strip().lstrip("<")
    return float(text) if re.fullmatch(r"\d+(\.\d+)?", text) else None


def clean_name(text):
    text = " ".join((text or "").replace("®", "").replace("™", "").replace("©", "").split())
    return re.sub(r"\s*[*∆~†◆#]+$", "", text).strip()


def load_kfc():
    """KFC's allergen & nutrition PDF: two side-by-side tables per page, values per item."""
    foods, portions, seen = [], {}, set()
    if not has_raw("kfc_nutrition.pdf.gz"):
        return foods, portions
    for table in pdf_tables("kfc_nutrition.pdf.gz"):
        for row in table:
            for half in (row[:13], row[13:26]):
                if len(half) < 13:
                    continue
                name = clean_name(half[0])
                kj, kcal, fat, sat, carbs, sugars, protein, salt = (number(x) for x in half[3:11])
                if not name or kcal is None or slug(name) in seen:
                    continue  # blank cell, header row or duplicate
                seen.add(slug(name))
                fid = f"kfc:{slug(name)}"
                foods.append(restaurant_food(fid, f"KFC {name}", "KFC", item_values({
                    "kcal": kcal, "fat": fat, "saturates": sat, "carbs": carbs, "sugars": sugars,
                    "protein": protein, "salt": salt}), per="item"))
                portions[fid] = [("item", 100.0)]
    return foods, portions


SUBWAY_SECTIONS = {  # section -> (name suffix, portions)
    "Subs": (" Sub", [("6-inch", 1), ("footlong", 2)]),
    "Saver Subs": (" Sub", [("6-inch", 1), ("footlong", 2)]),
    "Toasties": (" Toastie", None), "Wraps": (" Wrap", None), "Salads": (" Salad", None),
    "Spuds": (" Spud", None), "Protein Pots": (" Protein Pot", None),
}


def load_subway():
    """Subway's UK nutrition PDF: serving size (g), per serving and per 100 g values."""
    foods, portions, seen = [], {}, set()
    if not has_raw("subway_nutrition.pdf.gz"):
        return foods, portions
    for table in pdf_tables("subway_nutrition.pdf.gz"):
        section = None
        for row in table:
            name = clean_name(row[0])
            values = [number(x) for x in row[1:11]]
            if not name:
                continue
            if all(v is None for v in values):
                section = name  # a section heading such as "Subs" or "SAUCES & CONDIMENTS"
                continue
            size, _kj, kcal, fat, sat, carbs, sugars, fibre, protein, salt = values
            if not size or kcal is None:
                continue
            suffix, sizes = SUBWAY_SECTIONS.get(section, (None, None))
            if suffix is None:  # sides, cookies, breads, fillings, sauces...
                full = name if section in ("Sides", "Cookies", "DUNK POTS") else f"{name} ({(section or '').lower()})"
            else:
                full = name + suffix
            if slug(full) in seen:
                continue
            seen.add(slug(full))
            fid = f"subway:{slug(full)}"
            foods.append(restaurant_food(fid, f"Subway {full}", "Subway", label_values({
                "kcal": kcal, "fat": fat, "saturates": sat, "carbs": carbs, "sugars": sugars,
                "fibre": fibre, "protein": protein, "salt": salt}, size)))
            portions[fid] = [(d, size * m) for d, m in sizes] if sizes else [("item", size)]
    return foods, portions


BK_SKIP = re.compile(r"(?i)\btest\b|usa|tik ?tok|foyle|no meat|patty|condiment|\bpack\b|^\d+-pack|\bw/")


def load_burgerking():
    foods, portions, best = [], {}, {}
    if not has_raw("burgerking.json.gz"):
        return foods, portions
    for item in json.loads(raw("burgerking.json.gz"))["result"]:
        n = item.get("name")
        name = clean_name(n.get("en") if isinstance(n, dict) else n)
        name = re.sub(r"^[a-z]{1,3}(?=[A-Z0-9])", "", name)  # stray internal prefixes ("fsLarge...")
        nut = item.get("nutrition") or {}
        if len(name) < 3 or nut.get("calories") is None or BK_SKIP.search(name):
            continue
        if not nut.get("weight") and not item.get("sections"):
            continue  # weightless items outside the current menu are till codes and old/US items
        key = slug(name)
        if key not in best or (nut.get("weight") and not best[key][1].get("weight")):
            best[key] = (name, nut)
    for name, nut in best.values():
        fid = f"burgerking:{slug(name)}"
        label = {"kcal": nut.get("calories"), "fat": nut.get("fat"), "saturates": nut.get("saturatedFat"),
                 "carbs": nut.get("carbohydrates"), "sugars": nut.get("sugar"), "fibre": nut.get("fiber"),
                 "protein": nut.get("proteins"), "salt": nut.get("salt")}
        weight = nut.get("weight")
        if weight:
            foods.append(restaurant_food(fid, f"Burger King {name}", "Burger King", label_values(label, weight)))
            portions[fid] = [("item", weight)]
        else:
            foods.append(restaurant_food(fid, f"Burger King {name}", "Burger King", item_values(label), per="item"))
            portions[fid] = [("item", 100.0)]
    return foods, portions


MCD_NUTRIENTS = {"energy_kcal": "kcal", "fat": "fat", "saturated_fat": "saturates", "carbohydrate": "carbs",
                 "sugars": "sugars", "fibre": "fibre", "fiber": "fibre", "protein": "protein", "salt": "salt"}


def load_mcdonalds():
    """McDonald's item details: per-item values plus per-100 g ("hundred_g_per_product")."""
    foods, portions, seen = [], {}, set()
    if not has_raw("mcdonalds.json.gz"):
        return foods, portions
    for item in json.loads(raw("mcdonalds.json.gz")):
        name = clean_name(item.get("item_marketing_name") or item.get("item_name"))
        nutrients = ((item.get("nutrient_facts") or {}).get("nutrient")) or []
        if isinstance(nutrients, dict):
            nutrients = [nutrients]
        per_item, per100 = {}, {}
        for n in nutrients:
            key = MCD_NUTRIENTS.get(n.get("nutrient_name_id"))
            if key:
                per_item[key] = number(str(n.get("value")))
                per100[key] = number(str(n.get("hundred_g_per_product")))
        if not name or per_item.get("kcal") is None or name.lower() in seen:
            continue
        seen.add(name.lower())
        fid = f"mcdonalds:{item.get('item_id') or slug(name)}"
        kcal, kcal100 = per_item["kcal"], per100.get("kcal")
        if kcal100:  # weight = per item / per 100 g
            weight = kcal / kcal100 * 100.0
            foods.append(restaurant_food(fid, f"McDonald's {name}", "McDonald's", label_values(per_item, weight)))
            portions[fid] = [("item", round(weight, 1))]
        else:
            foods.append(restaurant_food(fid, f"McDonald's {name}", "McDonald's", item_values(per_item), per="item"))
            portions[fid] = [("item", 100.0)]
    return foods, portions


# --------------------------------------------------------------------------------------------
# Supermarket products (Open Food Facts). Keep in step with core/.../OpenFoodFacts.kt.
# --------------------------------------------------------------------------------------------

OFF_GROUP = "Supermarket"
OFF_NUTRIENTS = {  # Open Food Facts column -> (our nutrient, multiplier from grams)
    "energy-kcal_100g": ("energy", 1), "proteins_100g": ("protein", 1), "carbohydrates_100g": ("carbs", 1),
    "sugars_100g": ("sugars", 1), "fiber_100g": ("fiber", 1), "fat_100g": ("fat", 1),
    "saturated-fat_100g": ("saturated_fat", 1), "monounsaturated-fat_100g": ("mono_fat", 1),
    "polyunsaturated-fat_100g": ("poly_fat", 1), "cholesterol_100g": ("cholesterol", 1e3),
    "vitamin-a_100g": ("vitamin_a", 1e6), "vitamin-c_100g": ("vitamin_c", 1e3), "vitamin-d_100g": ("vitamin_d", 1e6),
    "calcium_100g": ("calcium", 1e3), "iron_100g": ("iron", 1e3), "potassium_100g": ("potassium", 1e3),
}
OFF_PACK = re.compile(r"^(?:(\d+)\s*[x\u00d7]\s*)?(\d+(?:[.,]\d+)?)\s*(g|ml)\b", re.IGNORECASE)


def off_number(text):
    try:
        return float(text.replace(",", "."))
    except (AttributeError, ValueError):
        return None


def off_portions(serving_size, serving_quantity, quantity):
    """"serving (30 g)" from the serving size, and "pack" when the pack is a single weight."""
    portions = []
    g = off_number(serving_quantity)
    if g is not None and 1 <= g <= 3000:
        label = serving_size.strip()
        portions.append((f"serving ({label})" if label and len(label) <= 40 else "serving", g))
    m = OFF_PACK.match(quantity.strip())
    pack = off_number(m.group(2)) if m else None
    if pack is not None and 1 <= pack <= 3000 and all(abs(p - pack) >= 0.5 for _, p in portions):
        portions.append(("pack (1 of a multipack)" if int(m.group(1) or 1) > 1 else "pack", pack))
    return portions


def off_case(text):
    """"WALKERS" -> "Walkers", "cornish pasty" -> "Cornish pasty"; mixed case is left alone."""
    if text.isupper() and len(text) > 4:
        return text.title().replace("'S", "'s")
    if text.islower():
        return text[:1].upper() + text[1:]
    return text


def load_off():
    foods, portions = [], {}
    if not has_raw("off_uk.tsv.gz"):
        return foods, portions
    for row in csv.DictReader(raw("off_uk.tsv.gz").splitlines(), delimiter="\t", quoting=csv.QUOTE_NONE):
        values = {}
        for col, (key, mult) in OFF_NUTRIENTS.items():
            v = off_number(row[col])
            if v is not None and v >= 0:
                values[key] = v * mult
        sodium, salt = off_number(row["sodium_100g"]), off_number(row["salt_100g"])
        if sodium is not None:
            values["sodium"] = sodium * 1000.0
        elif salt is not None:
            values["sodium"] = salt * 400.0
        brand = off_case(row["brand"])
        product = off_case(row["product_name"])
        name = product if not brand or product.lower().startswith(brand.lower()) else f"{brand} {product}"
        fid = f"off:{row['code']}"
        foods.append({"id": fid, "name": name, "group": OFF_GROUP, "source": brand or "Product", "common": "",
                      "values": values})
        p = off_portions(row["serving_size"], row["serving_quantity"], row["quantity"])
        if p:
            portions[fid] = p
    return foods, portions


# --------------------------------------------------------------------------------------------

def main():
    usda_portions = load_usda_portions()
    cofid = load_cofid()
    usda = load_usda()
    portions = {f"usda:{k}": v for k, v in usda_portions.items()}
    restaurants = []
    for loader in (load_greggs, load_costa, load_leon, load_mcdonalds, load_kfc, load_burgerking, load_subway,
                   load_nandos, load_popeyes):
        foods, p = loader()
        restaurants += foods
        portions.update(p)

    # Borrow USDA household measures ("1 medium") for common CoFID foods.
    by_name = {f["name"]: f["id"] for f in cofid}
    for line in (ROOT / "cofid_portion_links.tsv").read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        name, ndb = line.split("\t")
        if name not in by_name or ndb not in usda_portions:
            raise SystemExit(f"bad portion link: {line}")
        portions[by_name[name]] = usda_portions[ndb]

    supermarket, p = load_off()
    portions.update(p)

    foods = cofid + restaurants + supermarket + usda
    ids = [f["id"] for f in foods]
    seen = set()
    dupes = {i for i in ids if i in seen or seen.add(i)}
    assert not dupes, f"duplicate ids: {sorted(dupes)[:10]}"

    with open(ASSETS / "foods.tsv", "w", encoding="utf-8", newline="") as out:
        out.write("\t".join(["id", "name", "group", "source", "common", "per"] + NUTRIENTS) + "\n")
        for f in foods:
            cells = [f["id"], f["name"], f["group"], f["source"], f["common"], f.get("per", "100g")]
            cells += [fmt(f["values"].get(k)) for k in NUTRIENTS]
            out.write("\t".join(" ".join(c.split()) if c else "" for c in cells) + "\n")
    count = 0
    with open(ASSETS / "portions.tsv", "w", encoding="utf-8", newline="") as out:
        out.write("id\tdescription\tgrams\n")
        for fid in ids:
            for desc, grams in portions.get(fid, []):
                out.write(f"{fid}\t{desc}\t{grams:.4g}\n")
                count += 1

    by_source = {}
    for f in foods:
        source = "Supermarket" if f["group"] == OFF_GROUP else f["source"]
        by_source[source] = by_source.get(source, 0) + 1
    print(", ".join(f"{k} {v}" for k, v in by_source.items()), f"| total {len(foods)} foods, {count} portions")


if __name__ == "__main__":
    main()
