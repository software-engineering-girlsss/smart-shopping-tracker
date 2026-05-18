package com.shoppingplaner.repository

import com.shoppingplaner.model.Category
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRepository : JpaRepository<Category, Long> {
    fun findByParentIsNull(): List<Category>
    fun findByParentId(parentId: Long): List<Category>
    fun findBySlug(slug: String): Category?
}
