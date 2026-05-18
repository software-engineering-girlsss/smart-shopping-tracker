package service

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import model.Product
import model.StoreItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

class PicnicService(
    private val email: String,
    private val password: String,
    country: String = "de",
    private val debug: Boolean = false
) {
    companion object {
        private const val AGENT = "30100;1.15.233-10148"
        private const val DEVICE_ID_FILE = ".picnic-device-id"
        private const val TOKEN_FILE = ".picnic-auth-token"
    }

    private val BASE_URL = "https://storefront-prod.${country.lowercase()}.picnicinternational.com/api/15"

    private val client = OkHttpClient()
    private val deviceId: String = run {
        val f = File(DEVICE_ID_FILE)
        if (f.exists()) f.readText().trim()
        else {
            val id = UUID.randomUUID().toString()
            runCatching { f.writeText(id) }
            id
        }
    }
    private var authToken: String? = null
    private var debugPrinted = false
    private val searchLog = mutableListOf<JsonObject>()

    /** Write accumulated search log (raw responses + parsed results) to a JSON file. */
    fun writeSearchLog(file: File) {
        if (searchLog.isEmpty()) return
        val arr = com.google.gson.JsonArray()
        searchLog.forEach { arr.add(it) }
        file.writeText(com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(arr))
        println("Picnic search log saved to ${file.path}")
    }

    /** Decode a JWT and return the 'exp' claim as epoch seconds, or null on error. */
    private fun jwtExp(token: String): Long? = runCatching {
        val payload = token.split(".").getOrNull(1) ?: return null
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        val json = JsonParser.parseString(String(Base64.getUrlDecoder().decode(padded))).asJsonObject
        json.get("exp")?.asLong
    }.getOrNull()

    /** Try loading a previously saved token. Returns true if the token is still valid. */
    private fun loadCachedToken(): Boolean {
        val f = File(TOKEN_FILE)
        if (!f.exists()) return false
        val token = f.readText().trim()
        val exp = jwtExp(token)
        // Reject if expires within 5 minutes
        if (exp != null && exp < System.currentTimeMillis() / 1000 + 300) {
            if (debug) System.err.println("[Picnic debug] cached token expired, will re-login")
            f.delete()
            return false
        }
        authToken = token
        if (debug) System.err.println("[Picnic debug] using cached auth token (exp=$exp)")
        return true
    }

    private fun saveCachedToken(token: String) {
        runCatching { File(TOKEN_FILE).writeText(token) }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun login(): Boolean {
        // Try reusing a previously saved token (valid for ~6 months after 2FA)
        if (loadCachedToken()) {
            println("(using saved Picnic session)")
            return true
        }

        // client_id must be 30100 (not 1) — this is the official Picnic client ID
        val body = """{"key":"$email","secret":"${md5(password)}","client_id":30100}"""
        val request = Request.Builder()
            .url("$BASE_URL/user/login")
            .addHeader("x-picnic-did", deviceId)
            .addHeader("x-picnic-agent", AGENT)
            .addHeader("Content-Type", "application/json; charset=UTF-8")
            .post(body.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string() ?: "{}"
            if (!response.isSuccessful) {
                System.err.println("Picnic login failed (${response.code}): $bodyText")
                return false
            }

            authToken = response.header("x-picnic-auth")
            if (debug) {
                System.err.println("[Picnic debug] login status=${response.code} token=${authToken?.take(12)}... body=${bodyText.take(200)}")
            }

            if (authToken == null) {
                System.err.println("Picnic login: no x-picnic-auth token in response headers")
                return false
            }

            // Check if 2FA is required — inspect the boolean field, not just field name presence
            val json = runCatching { JsonParser.parseString(bodyText).asJsonObject }.getOrNull()
            val needs2fa = json?.get("second_factor_authentication_required")?.asBoolean ?: false
            if (needs2fa) {
                return handle2fa()
            }

            saveCachedToken(authToken!!)
            return true
        }
    }

    private fun handle2fa(): Boolean {
        // Step 1: trigger SMS
        val genBody = """{"channel":"SMS"}"""
        val genRequest = Request.Builder()
            .url("$BASE_URL/user/2fa/generate")
            .addHeader("x-picnic-auth", authToken!!)
            .addHeader("x-picnic-did", deviceId)
            .addHeader("x-picnic-agent", AGENT)
            .addHeader("Content-Type", "application/json; charset=UTF-8")
            .post(genBody.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        client.newCall(genRequest).execute().use { response ->
            if (debug) System.err.println("[Picnic debug] 2fa/generate status=${response.code}")
        }

        // Step 2: prompt user for the code
        print("Picnic 2FA: enter the code sent to your phone via SMS: ")
        val otp = readLine()?.trim() ?: return false

        // Step 3: verify
        val verifyBody = """{"otp":"$otp"}"""
        val verifyRequest = Request.Builder()
            .url("$BASE_URL/user/2fa/verify")
            .addHeader("x-picnic-auth", authToken!!)
            .addHeader("x-picnic-did", deviceId)
            .addHeader("x-picnic-agent", AGENT)
            .addHeader("Content-Type", "application/json; charset=UTF-8")
            .post(verifyBody.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        client.newCall(verifyRequest).execute().use { response ->
            if (!response.isSuccessful) {
                System.err.println("Picnic 2FA verify failed (${response.code}): ${response.body?.string()}")
                return false
            }
            // A new token may be issued after 2FA
            response.header("x-picnic-auth")?.let { authToken = it }
            saveCachedToken(authToken!!)
            return true
        }
    }

    fun search(product: Product): List<StoreItem> {
        val token = authToken ?: run {
            System.err.println("Picnic: not logged in, skipping search for '${product.name}'")
            return emptyList()
        }

        val query = product.name.encodeUrl()
        val url = "$BASE_URL/pages/search-page-results?search_term=$query"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-picnic-auth", token)
            .addHeader("x-picnic-did", deviceId)
            .addHeader("x-picnic-agent", AGENT)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string() ?: "[]"
                val entry = JsonObject().apply {
                    addProperty("query", product.name)
                    addProperty("url", url)
                    addProperty("status", response.code)
                }
                if (!response.isSuccessful) {
                    entry.addProperty("error", bodyText)
                    searchLog.add(entry)
                    if (response.code != 404) {
                        System.err.println("Picnic search failed (${response.code}) for '${product.name}': $bodyText")
                    }
                    println("  → ${product.name}: 0 candidates (HTTP ${response.code})")
                    return emptyList()
                }
                val rawJson = runCatching { JsonParser.parseString(bodyText) }.getOrNull()
                entry.add("raw_response", rawJson ?: com.google.gson.JsonPrimitive(bodyText))
                val results = if (rawJson != null) parseSearchResults(rawJson) else emptyList()
                entry.add("parsed_items", com.google.gson.JsonArray().also { arr ->
                    results.forEach { item ->
                        arr.add(JsonObject().apply {
                            addProperty("name", item.name)
                            addProperty("price", item.price)
                            addProperty("unit", item.unit)
                        })
                    }
                })
                searchLog.add(entry)
                println("  → ${product.name}: ${results.size} candidate(s)${if (results.isEmpty()) " — no results" else ""}")
                if (debug && !debugPrinted) {
                    System.err.println("[Picnic debug] URL: $url")
                    System.err.println("[Picnic debug] response:\n${bodyText.take(3000)}")
                    debugPrinted = true
                }
                results
            }
        } catch (e: Exception) {
            System.err.println("Picnic search error for '${product.name}': ${e.message}")
            emptyList()
        }
    }

    private fun parseSearchResults(json: com.google.gson.JsonElement): List<StoreItem> {
        val items = mutableListOf<StoreItem>()
        // Response is a FusionPage tree — recursively collect all "sellingUnit" objects
        collectSellingUnits(json, items)
        return items.take(5)
    }

    private fun collectSellingUnits(element: com.google.gson.JsonElement, out: MutableList<StoreItem>) {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                if (obj.has("sellingUnit")) {
                    val su = obj.get("sellingUnit")
                    if (su.isJsonObject) extractItem(su.asJsonObject)?.let { out.add(it) }
                }
                for ((_, value) in obj.entrySet()) {
                    collectSellingUnits(value, out)
                }
            }
            element.isJsonArray -> {
                for (child in element.asJsonArray) {
                    collectSellingUnits(child, out)
                }
            }
        }
    }

    private fun extractItem(obj: JsonObject): StoreItem? {
        val name = obj.get("name")?.asString ?: return null
        // Price is in cents
        val priceCents = obj.get("display_price")?.asLong
            ?: obj.get("price")?.asLong
            ?: return null
        val price = priceCents / 100.0
        val unit = obj.get("unit_quantity")?.asString ?: ""
        val id = obj.get("id")?.asString
        val url = if (id != null) "https://picnic.app/nl/product/$id" else null
        return StoreItem(name, price, unit, url)
    }

    private fun String.encodeUrl() = java.net.URLEncoder.encode(this, "UTF-8")
}
