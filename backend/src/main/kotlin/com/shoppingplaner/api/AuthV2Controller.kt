package com.shoppingplaner.api

import com.shoppingplaner.dto.AuthResponse
import com.shoppingplaner.dto.ConnectedAccountDto
import com.shoppingplaner.dto.ForgotPasswordRequest
import com.shoppingplaner.dto.LoginRequest
import com.shoppingplaner.dto.MessageResponse
import com.shoppingplaner.dto.PendingVerificationResponse
import com.shoppingplaner.dto.RefreshTokenRequest
import com.shoppingplaner.dto.ResendCodeRequest
import com.shoppingplaner.dto.ResetPasswordRequest
import com.shoppingplaner.dto.VerifyEmailRequest
import com.shoppingplaner.dto.UserV2Dto
import com.shoppingplaner.service.SupabaseAuthService
import com.shoppingplaner.service.SupabaseAuthResult
import com.shoppingplaner.dto.RegisterRequest
import com.shoppingplaner.service.SupabaseUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth v2", description = "Public authentication endpoints — proxy to Supabase")
@RestController
@RequestMapping("/api/v2/auth")
class AuthV2Controller(
    private val supabaseAuthService: SupabaseAuthService
) {
    private val log = LoggerFactory.getLogger(AuthV2Controller::class.java)

    @Operation(summary = "Sign in with email and password")
    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest): ResponseEntity<*> {
        return try {
            val result = supabaseAuthService.login(req.email, req.password)
            ResponseEntity.ok(toAuthResponse(result))
        } catch (e: Exception) {
            log.warn("POST /login failed: {}", e.message)
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("message" to (e.message ?: "Invalid credentials")))
        }
    }

    @Operation(summary = "Register a new account")
    @PostMapping("/register")
    fun register(@Valid @RequestBody req: RegisterRequest): ResponseEntity<*> {
        return try {
            val result = supabaseAuthService.register(req.email, req.password, req.name)
            if (result.accessToken.isNullOrBlank()) {
                ResponseEntity.status(HttpStatus.CREATED)
                    .body(PendingVerificationResponse(email = req.email))
            } else {
                ResponseEntity.status(HttpStatus.CREATED).body(toAuthResponse(result))
            }
        } catch (e: Exception) {
            log.warn("POST /register failed: {}", e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("message" to (e.message ?: "Registration failed")))
        }
    }

    @Operation(summary = "Verify email with 6-digit OTP code sent by Supabase")
    @PostMapping("/verify-email")
    fun verifyEmail(@Valid @RequestBody req: VerifyEmailRequest): ResponseEntity<*> {
        return try {
            val result = supabaseAuthService.verifyOtp(req.email, req.code)
            ResponseEntity.ok(toAuthResponse(result))
        } catch (e: Exception) {
            log.warn("POST /verify-email failed: {}", e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("message" to (e.message ?: "Invalid or expired code")))
        }
    }

    @Operation(summary = "Resend Supabase email verification code")
    @PostMapping("/resend-code")
    fun resendCode(@Valid @RequestBody req: ResendCodeRequest): ResponseEntity<*> {
        return try {
            supabaseAuthService.resendOtp(req.email)
            ResponseEntity.ok(MessageResponse("Code sent"))
        } catch (e: Exception) {
            log.warn("POST /resend-code failed: {}", e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("message" to (e.message ?: "Failed to resend code")))
        }
    }

    @Operation(summary = "Refresh access token")
    @PostMapping("/refresh")
    fun refresh(@RequestBody req: RefreshTokenRequest): ResponseEntity<*> {
        return try {
            val result = supabaseAuthService.refreshToken(req.refreshToken)
            ResponseEntity.ok(toAuthResponse(result))
        } catch (e: Exception) {
            log.warn("POST /refresh failed: {}", e.message)
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("message" to "Token refresh failed"))
        }
    }

    @Operation(summary = "Send password reset OTP to email")
    @PostMapping("/forgot-password")
    fun forgotPassword(@RequestBody req: ForgotPasswordRequest): ResponseEntity<MessageResponse> {
        supabaseAuthService.sendPasswordReset(req.email)
        return ResponseEntity.ok(MessageResponse("If an account with this email exists, a reset code has been sent"))
    }

    @Operation(summary = "Verify reset OTP and set new password")
    @PostMapping("/reset-password")
    fun resetPassword(@Valid @RequestBody req: ResetPasswordRequest): ResponseEntity<*> {
        return try {
            supabaseAuthService.verifyAndResetPassword(req.email, req.code, req.newPassword)
            ResponseEntity.ok(MessageResponse("Password reset successfully"))
        } catch (e: Exception) {
            log.warn("POST /reset-password failed: {}", e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("message" to (e.message ?: "Failed to reset password")))
        }
    }

    @Operation(summary = "Log out the current user")
    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<MessageResponse> {
        if (jwt != null) {
            val token = jwt.tokenValue
            Thread.ofVirtual().start { runCatching { supabaseAuthService.logout(token) } }
        }
        return ResponseEntity.ok(MessageResponse("Logged out successfully"))
    }

    private fun toAuthResponse(result: SupabaseAuthResult): AuthResponse {
        return AuthResponse(
            user = result.user.toDto(),
            accessToken = result.accessToken ?: throw RuntimeException("Missing access token"),
            refreshToken = result.refreshToken ?: throw RuntimeException("Missing refresh token")
        )
    }

    private fun SupabaseUser.toDto() = UserV2Dto(
        id = id,
        email = email,
        name = name,
        role = "user",
        createdAt = createdAt
    )
}
