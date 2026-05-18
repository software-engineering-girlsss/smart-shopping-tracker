package com.shoppingplaner.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "picnic_sessions")
data class PicnicSession(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val deviceId: String,

    @Column(length = 2048)
    val authToken: String?,

    val tokenExpiry: Long?,

    val updatedAt: Instant
)
