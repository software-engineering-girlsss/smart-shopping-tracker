package com.shoppingplaner.security

const val PRINCIPAL_ATTR = "authPrincipal"

sealed class AuthPrincipal {
    data class UserAccess(
        val userId: String,
        val picnicToken: String?,
        val zipCode: String? = null,
        val role: String = "user"
    ) : AuthPrincipal()
}
