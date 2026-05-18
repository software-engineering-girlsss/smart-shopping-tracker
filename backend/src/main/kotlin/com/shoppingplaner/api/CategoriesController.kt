package com.shoppingplaner.api

import com.shoppingplaner.dto.UnifiedProductDto
import com.shoppingplaner.repository.CategoryRepository
import com.shoppingplaner.service.ProductCatalogService
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class CategoryDto(
    val id: Long,
    val name: String,
    @JsonProperty("name_en") val nameEn: String,
    val slug: String,
    val icon: String?,
    @JsonProperty("sort_order") val sortOrder: Int,
    val children: List<CategoryDto> = emptyList()
)

@Tag(name = "Categories", description = "Product category hierarchy")
@RestController
@RequestMapping("/api/v2/categories")
class CategoriesController(
    private val categoryRepo: CategoryRepository,
    private val catalogService: ProductCatalogService,
) {

    @Operation(summary = "Get full category tree")
    @GetMapping
    fun getAll(): List<CategoryDto> {
        val all = categoryRepo.findAll()
        val byParent = all.groupBy { it.parent?.id }

        fun buildTree(parentId: Long?): List<CategoryDto> =
            (byParent[parentId] ?: emptyList())
                .sortedBy { it.sortOrder }
                .map { cat ->
                    CategoryDto(
                        id        = cat.id!!,
                        name      = cat.name,
                        nameEn    = cat.nameEn,
                        slug      = cat.slug,
                        icon      = cat.icon,
                        sortOrder = cat.sortOrder,
                        children  = buildTree(cat.id)
                    )
                }

        return buildTree(null)
    }

    @Operation(summary = "Get a single category by slug")
    @GetMapping("/{slug}")
    fun getBySlug(@PathVariable slug: String): ResponseEntity<CategoryDto> {
        val cat = categoryRepo.findBySlug(slug)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(CategoryDto(
            id        = cat.id!!,
            name      = cat.name,
            nameEn    = cat.nameEn,
            slug      = cat.slug,
            icon      = cat.icon,
            sortOrder = cat.sortOrder
        ))
    }

    @Operation(summary = "Get products for a category (served from DB cache)")
    @GetMapping("/{slug}/products")
    fun getProducts(
        @PathVariable slug: String,
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<List<UnifiedProductDto>> {
        val cat = categoryRepo.findBySlug(slug)
            ?: return ResponseEntity.notFound().build()
        val products = catalogService.findProductsByCategory(cat.id!!, limit)
        return ResponseEntity.ok(products)
    }
}
