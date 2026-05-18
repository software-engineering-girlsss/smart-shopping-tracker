package com.shoppingplaner.service

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.shoppingplaner.config.AppProperties
import com.shoppingplaner.model.PicnicSession
import com.shoppingplaner.model.SearchQuery
import com.shoppingplaner.model.StoreItem
import com.shoppingplaner.repository.PicnicSessionRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import com.github.benmanes.caffeine.cache.Caffeine
import com.shoppingplaner.profiling.OkHttpTimingListener
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.TimeUnit

/** Typed result of a Picnic login attempt. */
sealed class PicnicLoginResult {
    data class Success(val token: String) : PicnicLoginResult()
    /** Credentials accepted but account requires 2FA. [partialToken] is unverified. */
    data class Needs2FA(val partialToken: String) : PicnicLoginResult()
    object Failed : PicnicLoginResult()
}

/**
 * Picnic product search service.
 *
 * Auth tokens are persisted to the database so the service survives restarts
 * without requiring re-login. 2FA is handled via a separate verify step when credentials are present.
 *
 * Returns an empty list when no credentials are configured.
 */
@Service
class PicnicService(
    private val props: AppProperties,
    private val sessionRepo: PicnicSessionRepository,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(PicnicService::class.java)

    companion object {
        private const val AGENT = "30100;1.17.269-13059"

    }

    private val country   = props.picnic.country.lowercase()
    private val BASE_URL  = "https://storefront-prod.$country.picnicinternational.com/api/15"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .eventListenerFactory(OkHttpTimingListener.factory(meterRegistry, "picnic"))
        .build()
    private val deviceId: String

    private var authToken: String? = null

    // TTL + size-bounded cache: key is "token_suffix:query"
    private val searchCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build<String, List<StoreItem>>()

    fun currentAuthToken(): String? = authToken

    init {
        // Load or generate device ID from DB
        val session = sessionRepo.findTopByOrderByUpdatedAtDesc()
        deviceId = session?.deviceId ?: UUID.randomUUID().toString()

        // Restore a still-valid token from DB
        if (session?.authToken != null && isTokenValid(session.tokenExpiry)) {
            authToken = session.authToken
            log.info("Picnic: restored valid session from DB (deviceId={}...)", deviceId.take(8))
        }

        // Fallback 1: pre-configured token via APP_PICNIC_AUTH_TOKEN env var
        if (authToken == null && props.picnic.authToken.isNotBlank()) {
            if (isTokenValid(jwtExp(props.picnic.authToken))) {
                authToken = props.picnic.authToken
                log.info("Picnic: using pre-configured auth token from APP_PICNIC_AUTH_TOKEN")
            } else {
                log.warn("Picnic: APP_PICNIC_AUTH_TOKEN is set but expired — will attempt login")
            }
        }

        // Fallback 2: load token saved by CLI tool (.picnic-auth-token in common locations)
        if (authToken == null && props.picnic.email.isNotBlank()) {
            val candidatePaths = listOf(
                ".picnic-auth-token",
                "drafts/mmvp-rewe-picnic/.picnic-auth-token",
                "../drafts/mmvp-rewe-picnic/.picnic-auth-token",
                "/certs/.picnic-auth-token",
            )
            for (path in candidatePaths) {
                val file = java.io.File(path)
                if (file.exists()) {
                    val token = file.readText().trim()
                    if (token.isNotBlank() && isTokenValid(jwtExp(token))) {
                        authToken = token
                        log.info("Picnic: loaded valid token from CLI cache: {}", path)
                        break
                    }
                }
            }
        }
    }

    private fun isTokenValid(expiry: Long?): Boolean {
        if (expiry == null) return false
        return expiry > System.currentTimeMillis() / 1000 + 300
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun ensureLoggedIn(): Boolean {
        if (authToken != null) return true
        return login()
    }

    // Proactively refresh the token every 30 min — avoids the "dead zone" where
    // the token has expired but no search has been made yet to trigger re-login.
    @Scheduled(fixedRate = 30 * 60 * 1000)
    fun refreshTokenIfNeeded() {
        if (props.picnic.email.isBlank()) return
        val current = authToken
        val exp = if (current != null) jwtExp(current) else null
        val expiresInSeconds = exp?.minus(System.currentTimeMillis() / 1000)
        when {
            current == null -> {
                log.info("Picnic scheduler: no active token, attempting login")
                login()
            }
            expiresInSeconds != null && expiresInSeconds < 7200 -> {
                log.info("Picnic scheduler: token expires in {}min, refreshing proactively", expiresInSeconds / 60)
                authToken = null
                login()
            }
            else -> log.debug("Picnic scheduler: token valid for {}min, no action", (expiresInSeconds ?: 0) / 60)
        }
    }

    private fun login(): Boolean {
        if (props.picnic.email.isBlank()) {
            log.warn("Picnic: no credentials configured — Picnic search disabled")
            return false
        }
        val token = loginWithCredentials(props.picnic.email, props.picnic.password) ?: return false
        authToken = token
        persistSession()
        return true
    }

    /**
     * Authenticates with Picnic and returns a typed [PicnicLoginResult]:
     * - [PicnicLoginResult.Success] — full token, ready to use
     * - [PicnicLoginResult.Needs2FA] — partial token; call [generateOtp] then [authenticateOtp] to complete
     * - [PicnicLoginResult.Failed] — credentials wrong or network error
     */
    fun loginResult(email: String, password: String): PicnicLoginResult {
        val body = """{"key":"$email","secret":"${md5(password)}","client_id":30100}"""
        val request = Request.Builder()
            .url("$BASE_URL/user/login")
            .picnicHeaders(null)
            .post(body.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string() ?: "{}"
                if (!response.isSuccessful) {
                    log.error("Picnic login failed ({}): {}", response.code, bodyText.take(200))
                    return PicnicLoginResult.Failed
                }
                val token = response.header("x-picnic-auth")
                    ?: run {
                        log.error("Picnic login: no x-picnic-auth header in response")
                        return PicnicLoginResult.Failed
                    }
                val json = runCatching { JsonParser.parseString(bodyText).asJsonObject }.getOrNull()
                val needs2fa = json?.get("second_factor_authentication_required")?.asBoolean ?: false
                if (needs2fa) {
                    log.info("Picnic: 2FA required for {} — returning partial token", email.substringBefore("@"))
                    PicnicLoginResult.Needs2FA(token)
                } else {
                    log.info("Picnic: login successful for {}", email.substringBefore("@"))
                    PicnicLoginResult.Success(token)
                }
            }
        } catch (e: Exception) {
            log.error("Picnic login error: {}", e.message)
            PicnicLoginResult.Failed
        }
    }

    /**
     * No-op: Picnic sends the OTP automatically during login when 2FA is required.
     * No separate "generate" endpoint exists — this method always returns true.
     */
    fun generateOtp(partialToken: String): Boolean {
        log.info("Picnic: OTP was sent automatically during login — no separate trigger needed")
        return true
    }

    /**
     * Verifies the user-supplied [otp] using the [partialToken] from a previous [loginResult] call.
     * Returns the fully verified token on success, or null on wrong OTP / network error.
     * Endpoint: POST /user/2fa/verify with body {"otp":"..."}
     */
    fun authenticateOtp(partialToken: String, otp: String): String? {
        val body = """{"otp":"$otp"}"""
        val request = Request.Builder()
            .url("$BASE_URL/user/2fa/verify")
            .picnicHeaders(partialToken)
            .post(body.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string() ?: "{}"
                if (!response.isSuccessful) {
                    log.warn("Picnic 2fa/verify failed ({}): {}", response.code, bodyText.take(200))
                    return null
                }
                val newToken = response.header("x-picnic-auth")
                if (newToken != null) {
                    log.info("Picnic: 2FA verified — new token received in header")
                    return newToken
                }
                val json = runCatching { JsonParser.parseString(bodyText).asJsonObject }.getOrNull()
                val bodyToken = json?.get("x-picnic-auth")?.asString ?: json?.get("auth_token")?.asString
                if (bodyToken != null) {
                    log.info("Picnic: 2FA verified — token found in body")
                    return bodyToken
                }
                // Picnic reuses the partial token after verification on some responses
                log.info("Picnic: 2FA verified — no new token in response, reusing partial token")
                partialToken
            }
        } catch (e: Exception) {
            log.error("Picnic 2fa/verify error: {}", e.message)
            null
        }
    }

    /**
     * Backward-compat wrapper: returns the token string or null.
     * Returns null if 2FA is required (caller must use [loginResult] for full flow).
     */
    fun loginWithCredentials(email: String, password: String): String? =
        when (val r = loginResult(email, password)) {
            is PicnicLoginResult.Success -> r.token
            is PicnicLoginResult.Needs2FA -> {
                log.warn("Picnic auto-login: 2FA required for {} — cannot complete silently; re-auth skipped", email.substringBefore("@"))
                null
            }
            PicnicLoginResult.Failed -> null
        }

    private fun persistSession() {
        val token   = authToken ?: return
        val expiry  = jwtExp(token)
        val session = sessionRepo.findTopByOrderByUpdatedAtDesc()
        if (session != null) {
            sessionRepo.save(session.copy(authToken = token, tokenExpiry = expiry, updatedAt = Instant.now()))
        } else {
            sessionRepo.save(PicnicSession(deviceId = deviceId, authToken = token, tokenExpiry = expiry, updatedAt = Instant.now()))
        }
    }

    private fun jwtExp(token: String): Long? = runCatching {
        val payload = token.split(".").getOrNull(1) ?: return null
        val padded  = payload + "=".repeat((4 - payload.length % 4) % 4)
        JsonParser.parseString(String(Base64.getUrlDecoder().decode(padded))).asJsonObject
            .get("exp")?.asLong
    }.getOrNull()

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun Request.Builder.picnicHeaders(token: String? = authToken) = apply {
        addHeader("x-picnic-did", deviceId)
        addHeader("x-picnic-agent", AGENT)
        addHeader("Content-Type", "application/json; charset=UTF-8")
        token?.let { addHeader("x-picnic-auth", it) }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    /** Search using the owner's shared session (configured via env vars). */
    fun search(product: SearchQuery): List<StoreItem> {
        // Proactively clear a stale in-memory token so ensureLoggedIn() will re-login
        val current = authToken
        if (current != null && !isTokenValid(jwtExp(current))) {
            log.info("Picnic: session token expired at runtime — clearing for re-login (set APP_PICNIC_EMAIL+PASSWORD for auto-renewal)")
            authToken = null
        }

        if (!ensureLoggedIn()) {
            log.warn("Picnic: login failed, skipping search for '{}'", product.name)
            return emptyList()
        }

        val token = authToken ?: run {
            log.warn("Picnic: not logged in, skipping search for '{}'", product.name)
            return emptyList()
        }

        return searchWithToken(product, token)
    }

    /**
     * Search using an explicit Picnic auth token (e.g. from a user session).
     * Returns empty list on 401 — the caller should surface this as "session expired".
     */
    fun searchWithToken(product: SearchQuery, picnicToken: String): List<StoreItem> {
        val cacheKey = "${picnicToken.takeLast(8)}:${product.name}"
        searchCache.getIfPresent(cacheKey)?.let { return it }

        val url = "$BASE_URL/pages/search-page-results?search_term=${product.name.encodeUrl()}"
        val request = Request.Builder()
            .url(url)
            .picnicHeaders(picnicToken)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string() ?: "[]"
                if (!response.isSuccessful) {
                    if (response.code == 401) {
                        log.warn("Picnic: 401 for '{}' — token rejected. Clearing shared session for re-login.", product.name)
                        if (picnicToken == authToken) authToken = null
                    } else {
                        log.warn("Picnic search failed ({}) for '{}' — body: {}", response.code, product.name, bodyText.take(300))
                    }
                    return emptyList()
                }
                val rawJson = runCatching { JsonParser.parseString(bodyText) }.getOrNull()
                    ?: return emptyList()
                parseSearchResults(rawJson).also { results ->
                    if (results.isEmpty()) {
                        log.warn("Picnic '{}': 0 results — raw response (first 2000 chars): {}", product.name, bodyText.take(2000))
                    } else {
                        log.debug("Picnic '{}': {} result(s)", product.name, results.size)
                    }
                    searchCache.put(cacheKey, results)
                }
            }
        } catch (e: Exception) {
            log.error("Picnic search error for '{}': {}", product.name, e.message)
            emptyList()
        }
    }

    private fun parseSearchResults(json: JsonElement): List<StoreItem> {
        val items = mutableListOf<StoreItem>()
        collectSellingUnits(json, items)
        return items.take(5)
    }

    private fun collectSellingUnits(element: JsonElement, out: MutableList<StoreItem>) {
        if (out.size >= 5) return
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                if (obj.has("sellingUnit")) {
                    val su = obj.get("sellingUnit")
                    if (su.isJsonObject) extractItem(su.asJsonObject)?.let { out.add(it) }
                    if (out.size >= 5) return
                }
                for ((_, value) in obj.entrySet()) {
                    collectSellingUnits(value, out)
                    if (out.size >= 5) return
                }
            }
            element.isJsonArray -> {
                for (elem in element.asJsonArray) {
                    if (out.size >= 5) return
                    collectSellingUnits(elem, out)
                }
            }
        }
    }

    private fun extractItem(obj: JsonObject): StoreItem? {
        val name  = obj.get("name")?.asString ?: return null
        val cents = obj.get("display_price")?.asLong ?: obj.get("price")?.asLong ?: return null
        val unit  = obj.get("unit_quantity")?.asString ?: ""
        val id    = obj.get("id")?.asString
        val imageId = obj.get("image_id")?.asString
        val imageUrl = imageId?.let {
            "https://storefront-prod.$country.picnicinternational.com/static/images/$it/small.png"
        } ?: ""
        return StoreItem(name, cents / 100.0, unit, if (id != null) "https://picnic.app/de/product/$id" else null, imageUrl)
    }

    private fun String.encodeUrl() = java.net.URLEncoder.encode(this, "UTF-8")

    // ── Delivery info ─────────────────────────────────────────────────────────

    data class PicnicDeliveryInfo(
        val minimumOrderAmount: Double,
        val deliveryFee: Double
    )

    /**
     * Fetches delivery slot info to read minimum order and any delivery fee.
     * Returns null if not logged in or the call fails.
     */
    fun fetchDeliveryInfo(): PicnicDeliveryInfo? {
        val token = authToken ?: return null
        val request = Request.Builder()
            .url("$BASE_URL/delivery-slots")
            .picnicHeaders(token)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    log.debug("Picnic delivery-slots returned {}", response.code)
                    return null
                }
                val json = runCatching {
                    JsonParser.parseString(response.body?.string() ?: return null)
                }.getOrNull() ?: return null
                parseDeliveryInfo(json)
            }
        } catch (e: Exception) {
            log.debug("Picnic delivery info fetch failed: {}", e.message)
            null
        }
    }

    private fun parseDeliveryInfo(json: com.google.gson.JsonElement): PicnicDeliveryInfo? {
        return try {
            // Response can be a top-level array of slots, or an object with a "delivery_slots" array
            val slots = when {
                json.isJsonArray -> json.asJsonArray
                json.isJsonObject -> json.asJsonObject.getAsJsonArray("delivery_slots")
                    ?: json.asJsonObject.getAsJsonArray("slots")
                else -> null
            } ?: return null

            val first = slots.firstOrNull()?.asJsonObject ?: return null
            val minOrderCents = first.get("minimum_order_value")?.asLong
                ?: first.get("minimum_order_amount")?.asLong
                ?: return null
            val feeCents = first.get("delivery_price")?.asLong
                ?: first.get("delivery_fee")?.asLong
                ?: 0L

            PicnicDeliveryInfo(
                minimumOrderAmount = minOrderCents / 100.0,
                deliveryFee        = feeCents / 100.0
            )
        } catch (e: Exception) {
            log.debug("Picnic delivery info parse error: {}", e.message)
            null
        }
    }
}
