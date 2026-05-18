package com.shoppingplaner.repository

import com.shoppingplaner.model.Favorite
import org.springframework.data.jpa.repository.JpaRepository

interface FavoriteRepository : JpaRepository<Favorite, String> {
    fun findByUserId(userId: String): List<Favorite>
    fun findByUserIdAndType(userId: String, type: String): List<Favorite>
}
