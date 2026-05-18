package com.shoppingplaner.dev

import com.shoppingplaner.config.AppProperties
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
@Profile("dev")
class DevUserSeeder(private val props: AppProperties) {

    private val log = LoggerFactory.getLogger(DevUserSeeder::class.java)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = "application/json".toMediaType()

    companion object {
        const val TEST_EMAIL = "testuser@shoppingplaner.dev"
        const val TEST_PASSWORD = "Test1234!"
    }

    @EventListener(ApplicationReadyEvent::class)
    fun seed() {
        if (props.supabase.serviceKey.isBlank() || props.supabase.serviceKey.startsWith("REPLACE")) {
            log.warn("DevUserSeeder: APP_SUPABASE_SERVICE_KEY not set — skipping test user creation")
            return
        }

        val body = """
            {
              "email": "$TEST_EMAIL",
              "password": "$TEST_PASSWORD",
              "email_confirm": true
            }
        """.trimIndent().toRequestBody(json)

        val request = Request.Builder()
            .url("${props.supabase.url}/auth/v1/admin/users")
            .post(body)
            .header("Authorization", "Bearer ${props.supabase.serviceKey}")
            .header("apikey", props.supabase.serviceKey)
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful ->
                    log.info("DevUserSeeder: test user created — email={}, password={}", TEST_EMAIL, TEST_PASSWORD)
                response.code == 422 ->
                    log.info("DevUserSeeder: test user already exists — email={}", TEST_EMAIL)
                else ->
                    log.warn("DevUserSeeder: unexpected response {} — {}", response.code, response.body?.string())
            }
        }
    }
}
