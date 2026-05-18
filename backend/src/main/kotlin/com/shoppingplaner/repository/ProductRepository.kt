package com.shoppingplaner.repository

import com.shoppingplaner.model.Product
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProductRepository : JpaRepository<Product, String> {
    fun findByNormalizedNameAndQuantityUnitAndQuantityAmount(
        normalizedName: String, quantityUnit: String, quantityAmount: Double
    ): Product?

    @Query("SELECT p FROM Product p WHERE LOWER(p.normalizedName) LIKE LOWER(CONCAT('%', :term, '%'))")
    fun searchByNormalizedName(term: String): List<Product>

    fun findByCategoryId(categoryId: Long): List<Product>

    fun findByCategoryIsNull(): List<Product>

    @Query("SELECT p FROM Product p WHERE p.category IS NOT NULL ORDER BY p.updatedAt DESC")
    fun findRecentWithCategory(pageable: Pageable): List<Product>
}
