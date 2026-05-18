package com.shoppingplaner.repository

import com.shoppingplaner.model.Cart
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface CartRepository : JpaRepository<Cart, Long> {
    fun findByUserId(userId: String): Optional<Cart>
    fun findFirstByUserIdOrderByIdAsc(userId: String): Optional<Cart>
}
