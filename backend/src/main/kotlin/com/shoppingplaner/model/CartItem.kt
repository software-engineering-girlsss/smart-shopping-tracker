package com.shoppingplaner.model

import jakarta.persistence.*

@Entity
@Table(name = "cart_items")
data class CartItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val name: String,

    val quantity: Double,

    val unit: String,

    /** v2: specific store product ID (e.g. "rewe:12345"), null when item was added by free-text query */
    @Column(name = "product_id")
    val productId: String? = null,

    /** v2: comma-separated filter tags (e.g. "lactose-free,bio") */
    @Column(name = "tags_filter")
    val tagsFilter: String? = null,

    /** v2: user-chosen per-store products, stored as JSON: {"rewe":{"name":"...","price":1.29},"picnic":{...}} */
    @Column(name = "store_selections", columnDefinition = "TEXT")
    val storeSelectionsJson: String? = null,

    /** v2: optional link to canonical catalog product */
    @Column(name = "catalog_product_id")
    val catalogProductId: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    val cart: Cart
)
