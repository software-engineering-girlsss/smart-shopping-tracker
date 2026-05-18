package com.shoppingplaner.model

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "users")
data class User(
    @Id val id: String,
    val email: String? = null,
    val createdAt: Instant = Instant.now()
)
