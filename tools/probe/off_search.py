"""Temporary: compare Open Food Facts search endpoints (printed to the log)."""
import json, time, urllib.request, urllib.error
UA = "MealMacros/1.0 (Android; github.com/moontan318/MobileApp-FoodTracker)"
FIELDS = "code,product_name,product_name_en,brands,quantity,serving_size,serving_quantity,nutriments"
URLS = [
    "https://search.openfoodfacts.org/search?q=ginsters%20cornish%20pasty&page_size=5&fields=" + FIELDS,
    "https://search.openfoodfacts.org/search?q=tesco%20ham%20cheddar%20sandwich%20countries_tags:%22en:united-kingdom%22&page_size=5&langs=en&fields=" + FIELDS,
    "https://search.openfoodfacts.org/search?q=walkers%20oven%20baked&page_size=5&langs=en",
    "https://uk.openfoodfacts.org/cgi/search.pl?search_simple=1&action=process&json=1&page_size=5&fields=" + FIELDS + "&search_terms=ginsters%20cornish%20pasty",
    "https://world.openfoodfacts.org/api/v2/search?countries_tags_en=united-kingdom&brands_tags=ginsters&page_size=5&fields=" + FIELDS,
]
for url in URLS:
    t = time.time()
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"}), timeout=60) as r:
            body = r.read().decode()
            print("OK", r.status, round(time.time() - t, 1), "s", url)
    except urllib.error.HTTPError as e:
        print("HTTP", e.code, round(time.time() - t, 1), "s", url, e.read()[:300])
        continue
    except Exception as e:
        print("ERR", type(e).__name__, e, url); continue
    d = json.loads(body)
    print("  top keys:", list(d)[:15])
    items = d.get("hits") or d.get("products") or []
    for p in items[:3]:
        n = p.get("nutriments") or {}
        print("  ", p.get("code"), p.get("brands"), "|", p.get("product_name"), "|", p.get("quantity"), p.get("serving_size"), p.get("serving_quantity"),
              "| keys:", sorted(p)[:25], "| kcal", n.get("energy-kcal_100g"), "prot", n.get("proteins_100g"), "salt", n.get("salt_100g"), "nkeys", len(n))
    time.sleep(7)
