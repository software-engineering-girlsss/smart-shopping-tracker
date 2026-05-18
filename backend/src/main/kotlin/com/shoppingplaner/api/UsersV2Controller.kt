package com.shoppingplaner.api

import com.github.benmanes.caffeine.cache.Caffeine
import com.shoppingplaner.dto.*
import com.shoppingplaner.model.PicnicConnection
import com.shoppingplaner.repository.PicnicConnectionRepository
import com.shoppingplaner.security.PRINCIPAL_ATTR
import com.shoppingplaner.security.AuthPrincipal
import com.shoppingplaner.service.EncryptionService
import com.shoppingplaner.service.PicnicLoginResult
import com.shoppingplaner.service.PicnicService
import com.shoppingplaner.service.SupabaseAuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.concurrent.TimeUnit

/** Short-lived 2FA state stored server-side between /connect and /2fa-verify. */
private data class Pending2FA(
    val partialToken: String,
    val email: String,
    val plainPassword: String,
    val zipCode: String?
)

@Tag(name = "Users v2", description = "User account and connected-account management")
@RestController
@RequestMapping("/api/v2/users")
class UsersV2Controller(
    private val picnicConnectionRepo: PicnicConnectionRepository,
    private val picnicService: PicnicService,
    private val supabaseAuthService: SupabaseAuthService,
    private val encryptionService: EncryptionService
) {
    private val log = LoggerFactory.getLogger(UsersV2Controller::class.java)

    // Holds partial Picnic tokens while the user completes 2FA. Entries expire after 15 minutes.
    private val pending2fa = Caffeine.newBuilder()
        .expireAfterWrite(15, TimeUnit.MINUTES)
        .maximumSize(500)
        .build<String, Pending2FA>()

    @Operation(summary = "Get the current user's profile")
    @GetMapping("/me")
    fun getMe(
        @AuthenticationPrincipal jwt: Jwt?,
        request: HttpServletRequest
    ): ResponseEntity<UserV2Dto> {
        val principal = resolvePrincipal(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.ok(buildDto(principal, jwt))
    }

    @Operation(summary = "Update the current user's name")
    @PatchMapping("/me")
    fun updateMe(
        @RequestBody req: PatchMeRequest,
        @AuthenticationPrincipal jwt: Jwt?,
        request: HttpServletRequest
    ): ResponseEntity<UserV2Dto> {
        val principal = resolvePrincipal(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (req.name != null && jwt != null) {
            try { supabaseAuthService.updateUserMetadata(jwt.tokenValue, req.name) }
            catch (e: Exception) { log.warn("Failed to sync name to Supabase: ${e.message}") }
        }
        val updatedJwtName = req.name ?: extractName(jwt) ?: principal.userId
        val email = jwt?.getClaimAsString("email") ?: ""
        val picnicConnection = picnicConnectionRepo.findById(principal.userId).orElse(null)
        return ResponseEntity.ok(UserV2Dto(
            id = principal.userId,
            email = email,
            name = updatedJwtName,
            role = principal.role,
            connectedAccounts = picnicConnection?.toConnectedAccountDto()?.let { listOf(it) } ?: emptyList()
        ))
    }

    @Operation(
        summary = "Link a Picnic account — step 1",
        description = """Authenticates with Picnic using the provided credentials.

- **No 2FA**: returns 200 with the connected account. Token and password are encrypted (AES-256-GCM) at rest.
- **2FA required**: returns 202 with `needs_2fa: true`. Call `POST /me/accounts/picnic/2fa-verify` with the OTP
  sent to the user's registered 2FA channel (SMS/email) to complete the connection.

The plaintext password is never logged or returned."""
    )
    @PostMapping("/me/accounts/picnic")
    fun connectPicnic(
        @RequestBody req: ConnectPicnicRequest,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        val principal = resolvePrincipal(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Any>()

        return when (val result = picnicService.loginResult(req.email, req.password)) {
            is PicnicLoginResult.Success -> {
                saveConnection(principal.userId, result.token, req.email, req.password, req.zipCode)
                ResponseEntity.ok(ConnectedAccountDto(
                    email = req.email,
                    connectedAt = java.time.Instant.now().toString(),
                    expiresAt = jwtExp(result.token),
                    zipCode = req.zipCode
                ))
            }
            is PicnicLoginResult.Needs2FA -> {
                pending2fa.put(principal.userId, Pending2FA(
                    partialToken = result.partialToken,
                    email = req.email,
                    plainPassword = req.password,
                    zipCode = req.zipCode
                ))
                // Request OTP delivery to the user's registered 2FA method; best-effort.
                picnicService.generateOtp(result.partialToken)
                ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(PicnicNeeds2FADto(email = req.email))
            }
            PicnicLoginResult.Failed ->
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Any>()
        }
    }

    @Operation(
        summary = "Link a Picnic account — step 2 (2FA verification)",
        description = "Verifies the OTP sent to the user's 2FA channel after step 1 returned `needs_2fa: true`. On success, stores the fully verified token."
    )
    @PostMapping("/me/accounts/picnic/2fa-verify")
    fun verifyPicnic2fa(
        @RequestBody req: Verify2FARequest,
        request: HttpServletRequest
    ): ResponseEntity<ConnectedAccountDto> {
        val principal = resolvePrincipal(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val pending = pending2fa.getIfPresent(principal.userId)
            ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()

        val verifiedToken = picnicService.authenticateOtp(pending.partialToken, req.otp)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        pending2fa.invalidate(principal.userId)
        saveConnection(principal.userId, verifiedToken, pending.email, pending.plainPassword, pending.zipCode)
        return ResponseEntity.ok(ConnectedAccountDto(
            email = pending.email,
            connectedAt = java.time.Instant.now().toString(),
            expiresAt = jwtExp(verifiedToken),
            zipCode = pending.zipCode
        ))
    }

    @Operation(summary = "Unlink the connected Picnic account")
    @DeleteMapping("/me/accounts/picnic")
    fun disconnectPicnic(request: HttpServletRequest): ResponseEntity<Void> {
        val principal = resolvePrincipal(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        picnicConnectionRepo.deleteById(principal.userId)
        pending2fa.invalidate(principal.userId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Register a push notification token")
    @PostMapping("/me/push-tokens")
    fun addPushToken(@RequestBody req: PushTokenRequest, request: HttpServletRequest): ResponseEntity<Void> {
        resolvePrincipal(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @Operation(summary = "Remove a push notification token")
    @DeleteMapping("/me/push-tokens/{token}")
    fun deletePushToken(@PathVariable token: String, request: HttpServletRequest): ResponseEntity<Void> {
        resolvePrincipal(request) ?: return ResponseEntity.noContent().build()
        return ResponseEntity.noContent().build()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resolvePrincipal(request: HttpServletRequest): AuthPrincipal.UserAccess? =
        request.getAttribute(PRINCIPAL_ATTR) as? AuthPrincipal.UserAccess

    private fun buildDto(principal: AuthPrincipal.UserAccess, jwt: Jwt?): UserV2Dto {
        val picnicConnection = picnicConnectionRepo.findById(principal.userId).orElse(null)
        return UserV2Dto(
            id = principal.userId,
            email = jwt?.getClaimAsString("email") ?: "",
            name = extractName(jwt) ?: principal.userId,
            role = principal.role,
            connectedAccounts = picnicConnection?.toConnectedAccountDto()?.let { listOf(it) } ?: emptyList()
        )
    }

    private fun extractName(jwt: Jwt?): String? {
        if (jwt == null) return null
        jwt.getClaimAsString("name")?.takeIf { it.isNotBlank() }?.let { return it }
        @Suppress("UNCHECKED_CAST")
        val meta = runCatching { jwt.getClaim<Map<String, Any>>("user_metadata") }.getOrNull()
        return (meta?.get("full_name") ?: meta?.get("name"))?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun saveConnection(userId: String, token: String, email: String, plainPassword: String, zipCode: String?) {
        picnicConnectionRepo.save(PicnicConnection(
            userId = userId,
            email = email,
            authToken = encryptionService.encrypt(token),
            tokenExpiry = jwtExp(token),
            encryptedPassword = encryptionService.encrypt(plainPassword),
            zipCode = zipCode
        ))
    }

    private fun PicnicConnection.toConnectedAccountDto() = ConnectedAccountDto(
        email = email,
        connectedAt = connectedAt.toString(),
        expiresAt = tokenExpiry,
        zipCode = zipCode
    )

    private fun jwtExp(token: String): Long? = runCatching {
        val payload = token.split(".").getOrNull(1) ?: return null
        val padded  = payload + "=".repeat((4 - payload.length % 4) % 4)
        val json    = com.google.gson.JsonParser.parseString(
            String(java.util.Base64.getUrlDecoder().decode(padded))
        ).asJsonObject
        json.get("exp")?.asLong
    }.getOrNull()
}
