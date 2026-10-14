"""Temporary: stream the Open Food Facts CSV export and report UK coverage (printed to the log)."""
import csv, gzip, io, sys, urllib.request, collections, time
URL = "https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz"
csv.field_size_limit(10**9)
t = time.time()
req = urllib.request.Request(URL, headers={"User-Agent": "MealMacros/1.0 (data build; github.com/moontan318/MobileApp-FoodTracker)"})
resp = urllib.request.urlopen(req, timeout=600)
print("status", resp.status, resp.headers.get("Content-Length"))
reader = csv.DictReader(io.TextIOWrapper(gzip.GzipFile(fileobj=resp), encoding="utf-8", errors="replace"), delimiter="\t")
print("columns:", reader.fieldnames[:10], "...", len(reader.fieldnames))
cols = [c for c in reader.fieldnames if c.endswith("_100g")]
print("nutrient cols:", cols[:80])
print([c for c in reader.fieldnames if "scan" in c or "popular" in c or "serving" in c or c in ("quantity","brands","countries_tags","completeness","last_modified_t")])
total = uk = complete = 0
examples = []
scans = collections.Counter()
for row in reader:
    total += 1
    if "en:united-kingdom" not in (row.get("countries_tags") or ""):
        continue
    uk += 1
    if row.get("energy-kcal_100g") and row.get("proteins_100g") and row.get("carbohydrates_100g") and row.get("fat_100g") and row.get("product_name"):
        complete += 1
        n = (row.get("product_name") or "").lower(); b = (row.get("brands") or "").lower()
        if ("tesco" in b and "ham" in n and "cheddar" in n and "sandwich" in n) or ("walkers" in b and "oven baked" in n and "cheese" in n) or ("ginsters" in b and "pasty" in n):
            examples.append({k: row.get(k) for k in ("code","product_name","brands","quantity","serving_size","serving_quantity","energy-kcal_100g","proteins_100g","carbohydrates_100g","fat_100g","salt_100g","unique_scans_n","popularity_key")})
        try: scans[min(int(float(row.get("unique_scans_n") or 0)), 1000)//10*10] += 1
        except ValueError: pass
print("total", total, "uk", uk, "uk with macros", complete, "secs", int(time.time()-t))
print("scan buckets (unique_scans_n floor 10):", sorted(scans.items())[:30])
for e in examples[:40]: print(e)
