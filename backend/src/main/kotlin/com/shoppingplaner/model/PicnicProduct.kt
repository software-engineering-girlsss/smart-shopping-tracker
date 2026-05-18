package com.shoppingplaner.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "picnic_products")
data class PicnicProduct(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", nullable = false)
    val catalog: Product,

    @Column(name = "picnic_id", unique = true)
    val picnicId: String,

    @Column(name = "picnic_url")
    val picnicUrl: String? = null,

    @Column(name = "last_price")
    val lastPrice: Double? = null,

    val available: Boolean = true,

    @Column(name = "last_updated")
    val lastUpdated: Instant = Instant.now()
)
