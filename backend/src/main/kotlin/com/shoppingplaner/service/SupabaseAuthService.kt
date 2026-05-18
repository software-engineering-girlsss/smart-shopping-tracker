package com.shoppingplaner.service

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.shoppingplaner.config.AppProperties
import com.shoppingplaner.profiling.OkHttpTimingListener
import io.micrometer.core.instrument.MeterRegistry
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

data class SupabaseAuthResult(
    @SerializedName("access_token")  val accessToken: String?,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("token_type")    val tokenType: String = "bearer",
    @SerializedName("expires_in")    val expiresIn: Long = 3600,
    val user: SupabaseUser
)

data class SupabaseUser(
    val id: String,
    val email: String,
    @SerializedName("user_metadata") val userMetadata: Map<String, Any?> = emptyMap(),
    @SerializedName("created_at") val createdAt: String? = null
) {
    val name: String get() = userMetadata["name"]?.toString() ?: email.substringBefore("@")
}

@Service
class SupabaseAuthService(private val props: AppProperties, private val registry: MeterRegistry) {

    private val log = LoggerFactory.getLogger(SupabaseAuthService::class.java)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .eventListenerFactory(OkHttpTimingListener.factory(registry, "supabase"))
        .build()
    private val json = "application/json".toMediaType()
    private val gson = Gson()

    fun login(email: String, password: String): SupabaseAuthResult {
        val body = gson.toJson(mapOf("email" to email, "password" to password)).toRequestBody(json)
        val request = Request.Builder()
            .url("${props.supabase.url}/auth/v1/token?grant_type=password")
            .post(body)
            .header("apikey", props.supabase.anonKey)
            .header("Content-Type", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = runCatching {
                    gson.fromJson(responseBody, Map::class.java)["error_description"]?.toString()
                        ?: gson.fromJson(responseBody, Map::class.java)["msg"]?.toString()
                }.getOrNull() ?: "Invalid credentials"
                log.warn("Supabase login returned HTTP {}: {}", response.code, errorMsg)
                throw RuntimeException(errorMsg)
            }
            return gson.fromJson(responseBody, SupabaseAuthResult::class.java)
        }
    }

    fun register(email: String, password: String, name: String): SupabaseAuthResult {
        val body = gson.toJson(mapOf(
            "email" to email,
            "password" to password,
            "data" to mapOf("name" to name)
        )).toRequestBody(json)
        val request = Request.Builder()
            .url("${props.supabase.url}/auth/v1/signup")
            .post(body)
            .header("apikey", props.supabase.anonKey)
            .header("Content-Type", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = runCatching {
                    gson.fromJson(responseBody, Map::class.java)["msg"]?.toString()
                        ?: gson.fromJson(responseBody, Map::class.java)["error_description"]?.toString()
                }.getOrNull() ?: "Registration failed"
                log.warn("Supabase register returned HTTP {}: {}", response.code, errorMsg)
                throw RuntimeException(errorMsg)
            }
            // accessToken is null when email confirmation is required — caller handles this case
            return gson.fromJson(responseBody, SupabaseAuthResult::class.java)
        }
    }


    fun refreshToken(refreshToken: String): SupabaseAuthResult {
        val body = gson.toJson(mapOf("refresh_token" to refreshToken)).toRequestBody(json)
        val request = Request.Builder()
            .url("${props.supabase.url}/auth/v1/token?grant_type=refresh_token")
            .post(body)
            .header("apikey", props.supabase.anonKey)
            .header("Content-Type", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                log.warn("Supabase token refresh returned HTTP {}", response.code)
                throw RuntimeException("Token refresh failed: ${response.code}")
            }
            return gson.fromJson(responseBody, SupabaseAuthResult::class.java)
        }
    }

    fun verifyOtp(email: String, token: String): SupabaseAuthResult {
        val body = gson.toJson(mapOf("email" to email, "token" to token, "type" to "signup")).toRequestBody(json)
        val request = Request.Builder()
            .url("${props.supabase.url}/auth/v1/verify")
            .post(body)
            .header("apikey", props.supabase.anonKey)
            .header("Content-Type", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = runCatching {
                    gson.fromJson(responseBody, Map::class.java)["msg"]?.toString()
                        ?: gson.fromJson(responseBody, Map::class.java)["error_description"]?.toString()
                        ?: gson.fromJson(responseBody, Map::class.java)["error"]?.toString()
                }.getOrNull() ?: "Invalid or expired code"
                log.warn("Supabase verifyOtp returned HTTP {}: {}", response.code, errorMsg)
                throw RuntimeException(errorMsg)
            }
            return gson.fromJson(responseBody, SupabaseAuthResult::class.java)
        }
    }

    fun resendOtp(email: String) {
        val body = gson.toJson(mapOf("email" to email, "type" to "signup")).toRequestBody(json)
        val request = Request.Builder()
            .url("${props.supabase.url}/auth/v1/resend")
            .post(body)
            .header("apikey", props.supabase.anonKey)
            .header("Content-Type", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                log.warn("Supabase resendOtp returned HTTP {}", response.code)
                throw RuntimeException("Failed to resend code")
            }
        }
    }

    fun logout(userToken: String) {
        val request = Request.Builder()
            .url("${props.supabase.url}/auth/v1/logout")
            .post("".toRequestBody(json))
            .header("Authorization", "Bearer $userToken")
            .header("apikey", props.supabase.anonKey)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                log.warn("Supabase logout returned ${response.code} — treating as already logged out")
            }
        }
    }

    fun changePassword(userToken: String, newPassword: String) {
        val body = gson.toJson(mapOf("password" to newPassword)).toRequestBody(json)
        val request = Request.Builder()
            .url("${props.supabase.url}/auth/v1/user")
            .put(body)
            .header("Authorization", "Bearer $userToken")
            .header("apikey", props.supabase.anonKey)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Supabase password change failed: ${response.code}")
            }
        }
    }

    fun sendPasswordReset(email: String) {
        val body = gson.toJson(mapOf("email" to email)).toRequestBody(json)
        val request = Request.Builder()
            .url("${props.supabase.url}/auth/v1/recover")
            .post(body)
            .header("apikey", props.supabase.anonKey)
            .header("Content-Type", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            // Always succeed silently — never reveal whether the email exists
            if (!response.isSuccessful) {
                log.warn("Password reset request returned ${response.code}")
            }
        }
    }

    fun verifyAndResetPassword(email: String, token: String, newPassword: String) {
        val verifyBody = gson.toJson(
            mapOf("email" to email, "token" to token, "type" to "recovery")
        ).toRequestBody(json)
        val verifyRequest = Request.Builder()
            .url("${props.supabase.url}/auth/v1/verify")
            .post(verifyBody)
            .header("apikey", props.supabase.anonKey)
            .header("Content-Type", "application/json")
            .build()
        val accessToken = client.newCall(verifyRequest).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = runCatching {
                    gson.fromJson(responseBody, Map::class.java)["msg"]?.toString()
                        ?: gson.fromJson(responseBody, Map::class.java)["error_description"]?.toString()
                        ?: gson.fromJson(responseBody, Map::class.java)["error"]?.toString()
                }.getOrNull() ?: "Invalid or expired code"
                throw RuntimeException(errorMsg)
            }
            gson.fromJson(responseBody, SupabaseAuthResult::class.java).accessToken
                ?: throw RuntimeException("Invalid or expired code")
        }
        changePassword(accessToken, newPassword)
    }

    fun updateUserMetadata(userToken: String, name: String) {
        val body = gson.toJson(mapOf("data" to mapOf("name" to name))).toRequestBody(json)
        val request = Request.Builder()
            .url("${props.supabase.url}/auth/v1/user")
            .put(body)
            .header("Authorization", "Bearer $userToken")
            .header("apikey", props.supabase.anonKey)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                log.warn("Supabase user metadata update returned ${response.code}")
            }
        }
    }
}
