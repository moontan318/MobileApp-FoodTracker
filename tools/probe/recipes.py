"""Temporary: checks which recipe sites embed schema.org Recipe JSON-LD (printed to the log)."""
import json, re, urllib.request
URLS = [
    "https://www.bbc.co.uk/food/recipes/cheesy_green_pasta_bake_92655",
    "https://www.bbcgoodfood.com/recipes/easy-chicken-curry",
    "https://www.jamieoliver.com/recipes/pasta/spaghetti-bolognese/",
    "https://www.allrecipes.com/recipe/158968/spinach-and-feta-turkey-burgers/",
    "https://realfood.tesco.com/recipes/chicken-tikka-masala.html",
    "https://www.deliciousmagazine.co.uk/recipes/easy-chicken-curry/",
]
UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
def find(o):
    if isinstance(o, list):
        for x in o:
            r = find(x)
            if r: return r
    elif isinstance(o, dict):
        t = o.get("@type")
        if t == "Recipe" or (isinstance(t, list) and "Recipe" in t): return o
        for k in ("@graph", "mainEntity"):
            if k in o:
                r = find(o[k])
                if r: return r
    return None
for url in URLS:
    try:
        html = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "en-GB"}), timeout=40).read().decode("utf-8", "replace")
    except Exception as e:
        print("##", url, "ERROR", e); continue
    blocks = re.findall(r'<script[^>]*type="application/ld\+json"[^>]*>(.*?)</script>', html, re.S)
    recipe = None
    for b in blocks:
        try: recipe = find(json.loads(b))
        except Exception: pass
        if recipe: break
    print("##", url, "html", len(html), "ld blocks", len(blocks), "recipe", bool(recipe))
    if recipe:
        print(json.dumps({k: recipe.get(k) for k in ("name", "recipeYield", "recipeIngredient", "nutrition")}, ensure_ascii=False, indent=1)[:4000])
