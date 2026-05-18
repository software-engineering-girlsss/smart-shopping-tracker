package com.shoppingplaner.model

data class PriceResult(
    val store: String,
    val totalPrice: Double?,
    val currency: String = "EUR",
    val items: List<MatchedItem>
)
