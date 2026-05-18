# mmvp-rewe-picnic

Compare grocery prices across **REWE** and **Picnic** (German online grocery stores). Give it a shopping list; it searches both stores, uses GPT-4o-mini to pick the best match for each item, and prints a price table showing which store is cheaper overall.

```
Product                     REWE     Picnic
──────────────────────────────────────────
Milch 3,5% 1L               €1.09    €1.15
Butter 250g                 €2.29  ★ €1.99
Toilettenpapier 8er         €2.49  ★ €2.19
──────────────────────────────────────────
TOTAL                       €5.87  ★ €5.33

Cheapest: Picnic — €5.33  (saves €0.54)
```

Results are also exported to a CSV file.

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 21+ | e.g. `sudo apt install openjdk-21-jdk` |
| Python | 3.9+ | Only for the one-time REWE cert setup |
| `cryptography` Python lib | any | `pip3 install cryptography` |

---

## Setup

### 1. Clone & configure

```bash
git clone <repo-url>
cd mmvp-rewe-picnic
cp .env.template .env
```

Open `.env` and fill in your credentials (see sections below).

---

### 2. Picnic credentials

Register a free account at **picnic.app** (available in Germany and the Netherlands).

```env
PICNIC_EMAIL=your@email.com
PICNIC_PASSWORD=yourpassword
PICNIC_COUNTRY=de          # or nl
```

**First run:** Picnic requires 2FA — you'll get an SMS and be prompted to enter the OTP in the terminal. The device ID is saved to `.picnic-device-id` so subsequent runs skip the SMS.

---

### 3. REWE setup

#### 3a. Location

```env
REWE_SERVICE_TYPE=DELIVERY   # or PICKUP
REWE_ZIP_CODE=10115          # your postal code
```

For `PICKUP` mode, also set `REWE_MARKET_ID` (find it by inspecting REWE network requests for your chosen store).

#### 3b. mTLS certificate (required for prices)

REWE's mobile API uses mutual TLS. Extract the certificate from the REWE Android APK:

**Option A — from a connected Android device (easiest):**
```bash
pip3 install cryptography
python3 setup-rewe-cert.py      # pulls APK via ADB automatically
```

**Option B — from a downloaded APK:**
```bash
# Download the REWE APK (e.g. from APKMirror), then:
python3 setup-rewe-cert.py /path/to/rewe-base.apk
```

Both options produce `rewe.pem` and `rewe.key` in the project directory. The `.env.template` already points to these paths — no change needed unless you put them elsewhere.

#### 3c. Session cookie (required for REWE prices)

Without a session cookie, the REWE API returns product names but no prices.

1. Open [rewe.de](https://www.rewe.de) in your browser
2. Select your delivery address or store
3. Open DevTools → Application → Cookies → `www.rewe.de`
4. Copy the value of `rwSession` (or `rstp`)
5. Add to `.env`:
   ```env
   REWE_SESSION_COOKIE=rwSession=<paste-value-here>
   ```

> The session cookie expires periodically. If REWE prices stop showing, refresh it.

---

### 4. OpenAI API key

Used to expand abbreviated product names (e.g. `"Kn.FS Kürbiscr."` → `"Knorr Fix Kürbiscremesuppe"`) and to match the best product from each store's search results.

Get a key at [platform.openai.com/api-keys](https://platform.openai.com/api-keys). The free tier (15 req/min, 1500 req/day) is sufficient for typical shopping lists.

```env
OPENAI_API_KEY=sk-...
```

**Without an OpenAI key:** the tool still works — it falls back to the first search result from each store. Matching quality will be lower.

---

## Running

```bash
./gradlew run --args="products.txt"
```

The Gradle wrapper downloads everything on first run (no local Gradle install needed). Results are printed to the terminal and saved to `result.csv`.

### Using a product file

```
# products.txt — one product per line
# Format: name[, quantity[, unit]]
Milch 3.5%, 2, liter
Butter
Eier, 10, stk
Toilettenpapier
```

```bash
./gradlew run --args="-f products.txt"
```

### Using an existing receipt as baseline

If you have a receipt CSV (`name, qty, unit, unit_price`), pass it with `-c` to include the existing prices as a third column:

```bash
./gradlew run --args="-c my_receipt.csv"
```

### All options

```
Usage: mmvp-rewe-picnic [options] [product-file]

  -f, --file <file>    Product list (name, qty, unit — one per line)
  -c, --check <file>   Existing receipt CSV (name, qty, unit, unit_price)
  --no-picnic          Skip Picnic search
  --no-rewe            Skip REWE search
  --simplify           Strip brands and specifics; search by generic term
                       e.g. "Milch 3.5% Kaufland" → "Milch"
  -o, --output <file>  Output CSV path (default: result.csv)
  --debug              Log raw API responses to *.log files
  -h, --help           Show help
```

---

## How it works

1. **Normalize** — AI expands abbreviated receipt shorthand into searchable names (cached in `.normalization-cache.json` to avoid repeat API calls)
2. **Search** — Queries REWE and Picnic APIs for each product
3. **Match** — AI picks the best-matching product from each store's results (typically the cheapest compatible option)
4. **Output** — ASCII table in terminal + `result.csv`

---

## Project structure

```
src/main/kotlin/
├── Main.kt                     # CLI entry point & orchestration
├── model/Models.kt             # Data classes
├── service/
│   ├── PicnicService.kt        # Picnic API client (reverse-engineered)
│   ├── ReweService.kt          # REWE API client (mobile mTLS + legacy web)
│   ├── AiMatcherService.kt     # OpenAI integration for name matching
│   └── ExistingCheckService.kt # Receipt CSV parser
└── output/
    ├── PriceTablePrinter.kt    # ASCII table formatter
    └── CsvExporter.kt          # CSV export

setup-rewe-cert.py              # Extracts REWE mTLS cert from APK
.env.template                   # Configuration template
products.txt                    # Example product list
```

---

## Limitations

- **REWE prices require a session cookie** — anonymous requests return no prices
- **Picnic requires an account** — free to register, but NL/DE only
- **API compatibility** — both APIs are reverse-engineered and may break with app updates
- **OpenAI free tier** — 15 requests/min; slow for large lists but fine for typical shopping (10–20 items)
