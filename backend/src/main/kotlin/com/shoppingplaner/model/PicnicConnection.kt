package com.shoppingplaner.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "picnic_connections")
data class PicnicConnection(
    @Id
    val userId: String,

    val email: String,

    /** AES-256-GCM encrypted Picnic auth token: "base64(iv):base64(ciphertext)" */
    @Column(length = 2048)
    val authToken: String,

    val tokenExpiry: Long? = null,

    /** AES-256-GCM encrypted Picnic password — stored so the token can be refreshed silently. */
    @Column(length = 2048)
    val encryptedPassword: String? = null,

    /** User's Picnic delivery ZIP code for region-specific search results. */
    val zipCode: String? = null,

    val connectedAt: Instant = Instant.now()
)
