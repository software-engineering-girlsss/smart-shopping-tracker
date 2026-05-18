package com.shoppingplaner.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Persisted user favorite — either a specific store product ("specific")
 * or a free-text query with optional filters ("generic").
 */
@Entity
@Table(name = "favorites")
data class Favorite(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(nullable = false)
    val userId: String,

    /** "specific" or "generic" */
    @Column(nullable = false)
    val type: String,

    /** For type="specific": e.g. "rewe:12345" */
    val productId: String? = null,

    /** For type="generic": free-text search query */
    val query: String? = null,

    /** Comma-separated dietary/category tags */
    val filterTags: String? = null,

    val filterVolume: String? = null,

    val filterFatContent: String? = null,

    val filterBrand: String? = null,

    val createdAt: Instant = Instant.now()
)
