package com.shoppingplaner.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "product_translations")
data class ProductTranslation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "term_en", unique = true)
    val termEn: String,

    @Column(name = "term_de")
    val termDe: String,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)
