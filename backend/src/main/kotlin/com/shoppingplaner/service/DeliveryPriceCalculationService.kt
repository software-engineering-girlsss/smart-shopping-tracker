package com.shoppingplaner.service

data class BasketItem(
    val productId: String?,
    val name: String,
    val price: Double,
    val quantity: Int
)

data class DeliveryCostResult(
    val store: String,
    val fee: Double,
    val currency: String = "EUR",
    val isFree: Boolean,
    val basketTotal: Double,
    val minimumOrderAmount: Double?,
    val freeDeliveryThreshold: Double?,
    val amountToFreeDelivery: Double?,
    val hint: String?
)

interface DeliveryPriceCalculationService {
    val store: String
    fun calculate(items: List<BasketItem>): DeliveryCostResult
}
