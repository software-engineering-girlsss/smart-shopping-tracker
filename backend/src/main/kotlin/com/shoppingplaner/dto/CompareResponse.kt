package com.shoppingplaner.dto

data class CompareResponse(
    val products: List<ProductDto>,
    val stores: List<StoreCompareResult>,
    val cheapestStore: String?,
    val savings: Double?
)

data class StoreCompareResult(
    val store: String,
    val totalPrice: Double?,
    val currency: String = "EUR",
    val items: List<MatchedItemDto>
)

data class MatchedItemDto(
    val queryName: String,
    val matchedName: String,
    val price: Double,
    val url: String?,
    val imageUrl: String = ""
)
