package com.shoppingplaner.dto

data class CartDto(
    val id: Long?,
    val name: String,
    val items: List<CartItemDto>,
    val createdAt: String?
)

data class CartItemDto(
    val id: Long?,
    val name: String,
    val quantity: Double,
    val unit: String
)
