package com.shoppingplaner.model

data class MatchedItem(
    val queryName: String,
    val matchedName: String,
    val price: Double,
    val url: String?
)
