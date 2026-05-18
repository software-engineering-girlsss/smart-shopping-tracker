package com.shoppingplaner.api

import com.shoppingplaner.dto.ChangePasswordRequest
import com.shoppingplaner.dto.MessageResponse
import com.shoppingplaner.service.SupabaseAuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User Auth", description = "Authenticated user operations — require Supabase Bearer token")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/auth")
class UserAuthController(
    private val supabaseAuthService: SupabaseAuthService
) {

    @Operation(summary = "Log out", description = "Invalidates the current Supabase session server-side.")
    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<MessageResponse> {
        supabaseAuthService.logout(jwt.tokenValue)
        return ResponseEntity.ok(MessageResponse("Logged out successfully"))
    }

    @Operation(
        summary = "Change password",
        description = "Updates the authenticated user's password via Supabase. Minimum 8 characters."
    )
    @PutMapping("/change-password")
    fun changePassword(
        @Valid @RequestBody req: ChangePasswordRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<MessageResponse> {
        supabaseAuthService.changePassword(jwt.tokenValue, req.newPassword)
        return ResponseEntity.ok(MessageResponse("Password updated successfully"))
    }
}
