package com.shoppingplaner.repository

import com.shoppingplaner.model.ProductTranslation
import org.springframework.data.jpa.repository.JpaRepository

interface ProductTranslationRepository : JpaRepository<ProductTranslation, Long> {
    fun findByTermEnIgnoreCase(termEn: String): ProductTranslation?
}
