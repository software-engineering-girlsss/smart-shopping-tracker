package com.shoppingplaner.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.shoppingplaner.config.AppProperties
import com.shoppingplaner.model.User
import com.shoppingplaner.repository.UserRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class UserSyncService(
    private val props: AppProperties,
    private val userRepo: UserRepository,
) {
    private val log = LoggerFactory.getLogger(UserSyncService::class.java)
    private val mapper = jacksonObjectMapper()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    @EventListener(ApplicationReadyEvent::class)
    fun syncUsersFromSupabase() {
        val serviceKey = props.supabase.serviceKey
        if (serviceKey.isBlank() || serviceKey.startsWith("REPLACE")) {
            log.info("UserSyncService: APP_SUPABASE_SERVICE_KEY not set — skipping user import")
            return
        }

        val request = Request.Builder()
            .url("${props.supabase.url}/auth/v1/admin/users?per_page=1000")
            .get()
            .header("Authorization", "Bearer $serviceKey")
            .header("apikey", serviceKey)
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    log.warn("UserSyncService: Supabase returned {} — skipping import", response.code)
                    return
                }
                val body = response.body?.string() ?: return
                val root = mapper.readValue<Map<String, Any>>(body)
                @Suppress("UNCHECKED_CAST")
                val users = root["users"] as? List<Map<String, Any>> ?: emptyList()
                var imported = 0
                for (u in users) {
                    val id = u["id"] as? String ?: continue
                    val email = u["email"] as? String
                    if (!userRepo.existsById(id)) {
                        userRepo.save(User(id = id, email = email))
                        imported++
                    }
                }
                log.info("UserSyncService: imported {} new users from Supabase ({} total)", imported, users.size)
            }
        }.onFailure { log.warn("UserSyncService: failed to import users — {}", it.message) }
    }
}
