package com.shoppingplaner.dto

data class SearchResponse(
    val results: List<StoreResultDto>
)

data class StoreResultDto(
    val store: String,
    val items: List<StoreItemDto>
)

data class StoreItemDto(
    val name: String,
    val price: Double,
    val unit: String,
    val url: String?
)
