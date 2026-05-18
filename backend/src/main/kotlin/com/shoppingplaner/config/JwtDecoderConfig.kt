package com.shoppingplaner.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import javax.crypto.spec.SecretKeySpec

@Configuration
@Profile("!dev")
class JwtDecoderConfig(private val props: AppProperties) {

    @Bean
    fun jwtDecoder(): JwtDecoder {
        val secret = props.supabase.jwtSecret
        return if (secret.isNotBlank()) {
            // HS256 — symmetric secret (Supabase project using JWT Secret)
            NimbusJwtDecoder.withSecretKey(
                SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
            ).build()
        } else {
            // ES256 / RS256 — JWKS (Supabase projects with sb_publishable_ keys)
            NimbusJwtDecoder.withJwkSetUri("${props.supabase.url}/auth/v1/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build()
        }
    }
}
