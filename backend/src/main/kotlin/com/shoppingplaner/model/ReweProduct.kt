package com.shoppingplaner.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "rewe_products")
data class ReweProduct(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", nullable = false)
    val catalog: Product,

    @Column(name = "rewe_id", unique = true)
    val reweId: String,

    @Column(name = "rewe_url")
    val reweUrl: String? = null,

    @Column(name = "last_price")
    val lastPrice: Double? = null,

    val available: Boolean = true,

    @Column(name = "last_updated")
    val lastUpdated: Instant = Instant.now()
)
