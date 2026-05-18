package com.shoppingplaner.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PicnicLoginRequest(
    val email: String,
    val password: String
)

data class PicnicLoginResponse(
    @JsonProperty("session_token") val sessionToken: String,
    @JsonProperty("expires_at") val expiresAt: Long?
)

data class ChangePasswordRequest(
    @field:NotBlank
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    @JsonProperty("new_password") val newPassword: String
)

data class MessageResponse(val message: String)

data class ForgotPasswordRequest(val email: String)

data class ResetPasswordRequest(
    val email: String,
    @field:NotBlank val code: String,
    @field:NotBlank
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    @JsonProperty("new_password") val newPassword: String
)
