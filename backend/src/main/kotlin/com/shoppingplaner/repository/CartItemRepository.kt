package com.shoppingplaner.repository

import com.shoppingplaner.model.CartItem
import org.springframework.data.jpa.repository.JpaRepository

interface CartItemRepository : JpaRepository<CartItem, Long>
