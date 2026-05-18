# Developer & Agent Instructions — mmvp-rewe-picnic

## What This App Does

A **price-comparison CLI/app** that takes a grocery product list (or an existing shopping check/receipt) and compares total prices across:
- **Picnic** (online grocery, NL/DE market)
- **REWE** (online grocery, DE market)
- **Existing check** (baseline — the prices already paid or quoted in a given receipt/list)

Output: total price per variant, with the cheapest option highlighted.

---

## Project Stack

- **Language:** Kotlin, JVM 21
- **Build:** Gradle (Kotlin DSL), `build.gradle.kts`
- **No source files exist yet** — the Gradle skeleton is in place but `src/` is empty.

---

## What to Build First

### Phase 1 — Project skeleton
1. Create `src/main/kotlin/` and `src/test/kotlin/` directories.
2. Add a `Main.kt` entry point that reads a product list from stdin or a file argument.
3. Define a data model:
   ```kotlin
   data class Product(val name: String, val quantity: Double, val unit: String)
   data class PriceResult(val store: String, val totalPrice: Double, val currency: String, val items: List<MatchedItem>)
   data class MatchedItem(val query: Product, val matchedName: String, val price: Double, val url: String?)
   ```

### Phase 2 — Picnic integration
- Picnic has an **unofficial reverse-engineered REST API** (no official SDK).
- The maintained Node.js wrapper at https://github.com/MRVDH/picnic-api documents the endpoints — use those patterns directly via `OkHttp` or `ktor-client` in Kotlin.
- Auth flow: POST credentials → receive `authKey` → include in all subsequent requests as a header.
- Relevant endpoint: product search via the Catalog service.

### Phase 3 — REWE integration
- REWE has no official public API. The reverse-engineered endpoint is:
  - `GET https://shop.rewe.de/api/products/?query=<query>&marketID=<id>&serviceType=<PICKUP|DELIVERY>&zipCode=<PLZ>`
- **Service modes** — the API distinguishes two modes with different inventories and potentially different prices:
  - `serviceType=PICKUP` — stationary store; requires `marketID`
  - `serviceType=DELIVERY` — REWE Lieferservice (fulfillment center); requires `zipCode` (PLZ)
- **How to resolve marketID from PLZ** — call the service portfolio endpoint first:
  - `GET https://shop.rewe.de/api/marketselection/zipcodes/{zipCode}/services/pickup`
  - Returns available markets for that postal code; pick the first or let the user choose.
- Support both modes in config; let the user pick via env var or CLI flag.
- **⚠️ Cloudflare MTLS (as of March 2024):** REWE secured these endpoints with mutual TLS, requiring a client certificate extracted from the REWE Android APK. The Go project [ByteSizedMarius/rewerse-engineering](https://github.com/ByteSizedMarius/rewerse-engineering) documents how to handle this. **Start without MTLS** and add it only if plain requests return 403.
- Parse the JSON response to extract product name, price, and unit.
- Wrap in try/catch — endpoints are unstable; log failures and show "N/A" for the store.

### Phase 4 — AI product matching (Gemini)
- Product names differ between stores (e.g., "Bio Vollmilch 3,5%" vs "Whole Milk Organic"). Use the Gemini API to canonicalize and match products across stores.
- Use: `POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=YOUR_KEY`
- **Free tier:** Gemini 2.0 Flash is free — 15 requests/min, 1500 requests/day, no credit card required.
- Suggested prompt pattern: given a product name from the user's list, ask the model to pick the best match from a list of candidates returned by each store's search.

### Phase 5 — "Existing check" input
- Accept a plain-text or CSV file representing an existing receipt/check with columns: `product_name, quantity, unit_price`.
- This becomes the baseline "store" in the comparison.

---

## API Keys & Credentials to Obtain

### 1. Picnic Account (required)
- **Not an API key** — Picnic uses account credentials (email + password).
- **How to get:** Register a free account at [picnic.app](https://picnic.app) (available in NL and DE).
- Store as environment variables:
  ```
  PICNIC_EMAIL=your@email.com
  PICNIC_PASSWORD=yourpassword
  ```
- **Important:** Picnic may require phone verification (2FA) on first login. Handle `OTP_REQUIRED` responses in code.

### 2. REWE location config (no account needed)
- No credentials required; choose **one** of two modes:

  **Option A — Delivery by PLZ (recommended, simpler)**
  - Set your postal code. The app resolves the nearest fulfillment center automatically.
  - ```
    REWE_SERVICE_TYPE=DELIVERY
    REWE_ZIP_CODE=10115
    ```

  **Option B — Pickup from a specific store**
  - Find your local market ID: open `https://shop.rewe.de`, select your store, inspect network requests for the `marketID` parameter.
  - ```
    REWE_SERVICE_TYPE=PICKUP
    REWE_MARKET_ID=840174
    REWE_ZIP_CODE=10115
    ```
  - Note: `REWE_ZIP_CODE` is still useful for the market-resolution lookup even in pickup mode.

- Prices and product availability **can differ** between delivery and pickup modes.

### 3. Gemini API Key (required for AI matching — free)
- **How to get:** https://aistudio.google.com/apikey — sign in with a Google account, click "Create API key". No credit card needed.
- Free tier: 15 RPM, 1500 requests/day with `gemini-2.0-flash` — more than enough for this app.
- Store as:
  ```
  GEMINI_API_KEY=AIza...
  ```

### Environment variable setup
Create a `.env` file at the project root (already in `.gitignore`):
```
PICNIC_EMAIL=
PICNIC_PASSWORD=
REWE_MARKET_ID=
GEMINI_API_KEY=
```
Load it in Kotlin using a library like `dotenv-kotlin` (`io.github.cdimascio:dotenv-kotlin`).

---

## Recommended Dependencies to Add to `build.gradle.kts`

```kotlin
dependencies {
    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // JSON parsing
    implementation("com.google.code.gson:gson:2.11.0")
    // or kotlinx.serialization:
    // implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // .env loading
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    testImplementation(kotlin("test"))
}
```

---

## Architecture Suggestion

```
src/main/kotlin/
  Main.kt                  # entry point, reads input, calls services, prints table
  model/
    Product.kt
    PriceResult.kt
  service/
    PicnicService.kt        # HTTP calls to Picnic API
    ReweService.kt          # HTTP calls to REWE API
    ExistingCheckService.kt # parses a receipt file
    AiMatcherService.kt     # calls OpenAI to match product names
  output/
    PriceTablePrinter.kt    # formats and prints the comparison table
```

---

## Input Format (suggested)

Plain-text or CSV file, one product per line:
```
Milch 3.5%, 2, liter
Butter, 250, g
Eier, 10, stk
```
Or as a JSON file for structured input.

---

## Key Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| REWE endpoints break silently | Wrap in try/catch, log warning, show "N/A" for that store |
| Picnic 2FA required | Detect OTP response, prompt user for OTP on stdin |
| Product name mismatch across stores | AI matching (Phase 4) or fuzzy string match as fallback |
| Gemini rate limit (15 RPM free) | Batch product-name lookups into one prompt per store instead of one per item |
| Picnic only available in NL/DE | Document clearly; add a region flag if needed |

---

## Where to Start (Checklist)

- [ ] Register a Picnic account and confirm login works
- [ ] Get a Gemini API key
- [ ] Find a REWE market ID for your local store
- [ ] Create `src/main/kotlin/Main.kt` with a minimal stub
- [ ] Add dependencies to `build.gradle.kts`
- [ ] Implement `ReweService` first (no auth, easier to test)
- [ ] Implement `PicnicService` second (needs credentials)
- [ ] Implement `AiMatcherService` with a simple prompt
- [ ] Wire everything together in `Main.kt`
- [ ] Add `ExistingCheckService` for the receipt/check input
- [ ] Test with a 5-item grocery list end-to-end
