package com.shoppingplaner.dto

data class SearchRequest(
    val query: String,
    val quantity: Double = 1.0,
    val unit: String = "stk"
)
