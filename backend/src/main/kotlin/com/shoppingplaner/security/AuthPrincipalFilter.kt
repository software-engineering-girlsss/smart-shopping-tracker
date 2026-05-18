package com.shoppingplaner.security

import com.shoppingplaner.model.PicnicConnection
import com.shoppingplaner.model.User
import com.shoppingplaner.repository.PicnicConnectionRepository
import com.shoppingplaner.repository.UserRepository
import com.shoppingplaner.service.EncryptionService
import com.shoppingplaner.service.PicnicService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AuthPrincipalFilter(
    private val picnicConnectionRepo: PicnicConnectionRepository,
    private val userRepo: UserRepository,
    private val encryptionService: EncryptionService,
) : OncePerRequestFilter() {

    // @Lazy to break the potential circular dependency with PicnicService (which may depend on config beans)
    @Autowired @Lazy
    private lateinit var picnicService: PicnicService

    private val log = LoggerFactory.getLogger(AuthPrincipalFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication is JwtAuthenticationToken) {
            val jwt = authentication.token
            val sub = jwt.subject
            if (!sub.isNullOrBlank()) {
                val email = runCatching { jwt.getClaim<String>("email") }.getOrNull()
                if (!userRepo.existsById(sub)) {
                    try { userRepo.save(User(id = sub, email = email)) }
                    catch (_: DataIntegrityViolationException) { /* concurrent insert — ignore */ }
                    catch (e: Exception) { log.error("Failed to upsert user sub={}: {}", sub, e.message) }
                }

                val (picnicToken, zipCode) = resolveConnection(sub)

                @Suppress("UNCHECKED_CAST")
                val appMetadata = runCatching { jwt.getClaim<Map<String, Any>>("app_metadata") }.getOrNull()
                val role = appMetadata?.get("role")?.toString() ?: "user"
                request.setAttribute(PRINCIPAL_ATTR, AuthPrincipal.UserAccess(
                    userId = sub,
                    picnicToken = picnicToken,
                    zipCode = zipCode,
                    role = role
                ))
            }
        }
        chain.doFilter(request, response)
    }

    /**
     * Loads the stored Picnic connection for [userId], decrypts the token, and attempts a
     * silent re-authentication if the token is expired and an encrypted password is available.
     * Returns (decryptedToken, zipCode) — either field may be null on failure.
     */
    private fun resolveConnection(userId: String): Pair<String?, String?> {
        val connection = runCatching { picnicConnectionRepo.findById(userId).orElse(null) }
            .onFailure { log.error("Failed to load Picnic connection for sub={}: {}", userId, it.message) }
            .getOrNull() ?: return null to null

        val decryptedToken = encryptionService.decrypt(connection.authToken)

        val nowEpochSecs = System.currentTimeMillis() / 1000
        val isExpired = connection.tokenExpiry != null && connection.tokenExpiry < nowEpochSecs

        if (isExpired && connection.encryptedPassword != null) {
            val refreshed = tryReauth(connection)
            if (refreshed != null) return refreshed to connection.zipCode
            log.warn("Silent Picnic re-auth failed for userId={} — returning null token", userId)
            return null to connection.zipCode
        }

        return decryptedToken to connection.zipCode
    }

    private fun tryReauth(connection: PicnicConnection): String? = runCatching {
        val password = encryptionService.decrypt(connection.encryptedPassword!!) ?: return null
        val newToken = picnicService.loginWithCredentials(connection.email, password) ?: return null
        val expiry = jwtExp(newToken)
        picnicConnectionRepo.save(connection.copy(
            authToken = encryptionService.encrypt(newToken),
            tokenExpiry = expiry
        ))
        log.info("Silent Picnic re-auth succeeded for userId={}", connection.userId)
        newToken
    }.onFailure {
        log.error("Silent Picnic re-auth threw for userId={}: {}", connection.userId, it.message)
    }.getOrNull()

    private fun jwtExp(token: String): Long? = runCatching {
        val payload = token.split(".").getOrNull(1) ?: return null
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        val json = com.google.gson.JsonParser.parseString(
            String(java.util.Base64.getUrlDecoder().decode(padded))
        ).asJsonObject
        json.get("exp")?.asLong
    }.getOrNull()
}
