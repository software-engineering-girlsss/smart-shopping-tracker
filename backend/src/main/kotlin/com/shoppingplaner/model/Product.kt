package com.shoppingplaner.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "products")
data class Product(
    @Id
    val id: String,

    val name: String,

    @Column(name = "normalized_name")
    val normalizedName: String,

    @Column(name = "image_url")
    val imageUrl: String? = null,

    @Column(name = "quantity_amount")
    val quantityAmount: Double = 1.0,

    @Column(name = "quantity_unit")
    val quantityUnit: String = "stck",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    val category: Category? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    val updatedAt: Instant = Instant.now()
)
