package com.shoppingplaner.dto

data class CompareRequest(
    val products: List<ProductDto>
)

data class ProductDto(
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "stk"
)
