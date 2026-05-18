package com.shoppingplaner.model

import jakarta.persistence.*

@Entity
@Table(name = "delivery_rules")
data class DeliveryRule(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "store_id", nullable = false)
    val storeId: String,

    @Column(name = "min_basket_amount")
    val minBasketAmount: Double?,

    @Column(name = "max_basket_amount")
    val maxBasketAmount: Double?,

    @Column(name = "delivery_fee", nullable = false)
    val deliveryFee: Double,

    @Column(name = "minimum_order_amount")
    val minimumOrderAmount: Double?,

    @Column(name = "free_delivery_threshold")
    val freeDeliveryThreshold: Double?,

    val note: String?
)
