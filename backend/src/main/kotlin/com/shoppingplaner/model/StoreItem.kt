package com.shoppingplaner.model

data class StoreItem(
    val name: String,
    val price: Double,
    val unit: String,
    val url: String?,
    val imageUrl: String = ""
)
