package com.shoppingplaner.config

import com.shoppingplaner.security.AuthPrincipalFilter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@EnableWebSecurity
@EnableCaching
@Configuration
class SecurityConfig(
    @Lazy private val authPrincipalFilter: AuthPrincipalFilter,
    @Value("\${APP_MANAGEMENT_PASSWORD:}") private val managementPassword: String,
) {
    private val log = LoggerFactory.getLogger(SecurityConfig::class.java)

    // Provides credentials for actuator HTTP Basic Auth.
    // If APP_MANAGEMENT_PASSWORD is not set, nobody can authenticate — actuator (except /health) is inaccessible.
    @Bean
    fun managementUserDetailsService(): UserDetailsService {
        if (managementPassword.isBlank()) {
            log.warn("APP_MANAGEMENT_PASSWORD not set — /actuator/** endpoints are inaccessible (except /actuator/health)")
            return UserDetailsService { throw UsernameNotFoundException("actuator access disabled: set APP_MANAGEMENT_PASSWORD") }
        }
        val user = User.builder()
            .username("actuator")
            .password("{noop}$managementPassword")
            .roles("ACTUATOR")
            .build()
        log.info("Actuator management user configured")
        return InMemoryUserDetailsManager(user)
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("*")
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = false
            maxAge = 3600L
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }

    // Dedicated filter chain for actuator endpoints — HTTP Basic Auth only.
    // Runs before the main JWT chain (Order 1). /actuator/health stays public for Render health checks.
    @Bean
    @Order(1)
    fun actuatorSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher("/actuator/**")
        http {
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            httpBasic { }
            authorizeHttpRequests {
                authorize("/actuator/health", permitAll)
                authorize(anyRequest, hasRole("ACTUATOR"))
            }
        }
        return http.build()
    }

    // Main API filter chain — JWT Bearer tokens only, no Basic Auth here.
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val delegate = DefaultBearerTokenResolver()
        http {
            cors { }
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                authorize(HttpMethod.OPTIONS, "/**", permitAll)
                authorize("/auth/**", permitAll)
                authorize("/api/v2/auth/**", permitAll)
                authorize("/health", permitAll)
                authorize("/api/v1/health", permitAll)
                authorize("/api/v2/health", permitAll)
                authorize("/swagger-ui.html", permitAll)
                authorize("/swagger-ui/**", permitAll)
                authorize("/v3/api-docs/**", permitAll)
                authorize("/h2-console/**", permitAll)
                authorize("/", permitAll)
                authorize("/index.html", permitAll)
                authorize("/*.js", permitAll)
                authorize("/*.css", permitAll)
                authorize("/*.ico", permitAll)
                authorize("/assets/**", permitAll)
                authorize("/api/v2/images/proxy", permitAll)
                authorize("/error", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer {
                // Don't reject requests with invalid/expired tokens on public auth endpoints.
                // Without this, Spring Security returns 401 before the controller even sees the request,
                // making it impossible to log in when the client still has an old token in storage.
                bearerTokenResolver = org.springframework.security.oauth2.server.resource.web.BearerTokenResolver { req ->
                    if (req.requestURI.startsWith("/api/v2/auth/")) null else delegate.resolve(req)
                }
                jwt { }
            }
        }
        http.addFilterAfter(authPrincipalFilter, BearerTokenAuthenticationFilter::class.java)
        return http.build()
    }
}
