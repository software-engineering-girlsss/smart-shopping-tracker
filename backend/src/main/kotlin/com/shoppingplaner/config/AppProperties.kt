package com.shoppingplaner.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val supabase: SupabaseProperties = SupabaseProperties(),
    val rewe: ReweProperties = ReweProperties(),
    val picnic: PicnicProperties = PicnicProperties(),
    val openai: OpenAiProperties = OpenAiProperties(),
    val encryptionKey: String = ""
) {
    data class SupabaseProperties(
        val url: String = "",
        val anonKey: String = "",
        val serviceKey: String = "",
        val jwtSecret: String = "",
        val emailRedirectTo: String = ""
    )
    data class ReweProperties(
        val serviceType: String = "DELIVERY",
        val zipCode: String = "",
        val marketId: String = "",
        val certFile: String = "",
        val keyFile: String = ""
    )

    data class PicnicProperties(
        val email: String = "",
        val password: String = "",
        val country: String = "de",
        val authToken: String = ""
    )

    data class OpenAiProperties(
        val apiKey: String = "",
        val model: String = "gpt-4o-mini"
    )

}
