package com.shoppingplaner.model

data class MatchCacheEntry(
    val hasMatch: Boolean,
    val matchedName: String = "",
    val matchedPrice: Double = 0.0,
    val matchedUnit: String = "",
    val matchedUrl: String? = null,
    val matchedImageUrl: String = "",
)
