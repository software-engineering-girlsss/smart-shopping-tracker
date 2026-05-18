package com.shoppingplaner.dev

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import java.time.Instant

@Configuration
@Profile("dev")
class DevJwtDecoderConfig {

    @Bean
    fun jwtDecoder(): JwtDecoder = JwtDecoder { token ->
        if (token == "dev-test-token") {
            Jwt.withTokenValue(token)
                .header("alg", "none")
                .claim("sub", "dev-user-id")
                .claim("email", "dev@test.local")
                .claim("name", "Dev User")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build()
        } else {
            throw JwtException("dev profile only accepts dev-test-token")
        }
    }
}
