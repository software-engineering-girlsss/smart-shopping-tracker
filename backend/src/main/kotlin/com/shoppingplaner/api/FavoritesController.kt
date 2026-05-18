package com.shoppingplaner.api

import com.shoppingplaner.dto.*
import com.shoppingplaner.model.Favorite
import com.shoppingplaner.repository.FavoriteRepository
import com.shoppingplaner.security.PRINCIPAL_ATTR
import com.shoppingplaner.security.AuthPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@Tag(name = "Favorites v2", description = "User favorites — specific products or generic queries")
@RestController
@RequestMapping("/api/v2/favorites")
@Transactional
class FavoritesController(
    private val favoriteRepo: FavoriteRepository
) {

    @Operation(
        summary = "List the current user's favorites",
        description = "Optionally filter by type: `specific` (a product in a specific store) or `generic` (a free-text query)."
    )
    @GetMapping
    fun list(
        @RequestParam(required = false) type: String?,
        request: HttpServletRequest
    ): ResponseEntity<List<FavoriteV2Dto>> {
        val userId = resolveUserId(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val favs = if (type != null)
            favoriteRepo.findByUserIdAndType(userId, type)
        else
            favoriteRepo.findByUserId(userId)
        return ResponseEntity.ok(favs.map { it.toDto() })
    }

    @Operation(summary = "Add a new favorite")
    @PostMapping
    fun add(@RequestBody req: AddFavoriteRequest, request: HttpServletRequest): ResponseEntity<FavoriteV2Dto> {
        val userId = resolveUserId(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val fav = favoriteRepo.save(
            Favorite(
                userId          = userId,
                type            = req.type,
                productId       = req.productId,
                query           = req.query,
                filterTags      = req.filters?.tags?.joinToString(","),
                filterVolume    = req.filters?.volume,
                filterFatContent = req.filters?.fatContent,
                filterBrand     = req.filters?.brand
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(fav.toDto())
    }

    @Operation(summary = "Remove a favorite")
    @DeleteMapping("/{favoriteId}")
    fun delete(@PathVariable favoriteId: String, request: HttpServletRequest): ResponseEntity<Void> {
        val userId = resolveUserId(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val fav = favoriteRepo.findById(favoriteId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        if (fav.userId != userId) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        favoriteRepo.delete(fav)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Get current deals for the user's favorites")
    @GetMapping("/deals")
    fun deals(request: HttpServletRequest): ResponseEntity<List<FavoriteDealDto>> {
        resolveUserId(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        // Deal matching is a future feature — returns empty list for now
        return ResponseEntity.ok(emptyList())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resolveUserId(request: HttpServletRequest): String? =
        (request.getAttribute(PRINCIPAL_ATTR) as? AuthPrincipal.UserAccess)?.userId

    private fun Favorite.toDto() = FavoriteV2Dto(
        id        = id,
        type      = type,
        productId = productId,
        query     = query,
        filters   = if (filterTags != null || filterVolume != null || filterFatContent != null || filterBrand != null)
            CartItemFiltersDto(
                tags       = filterTags?.split(",")?.filter { it.isNotBlank() },
                volume     = filterVolume,
                fatContent = filterFatContent,
                brand      = filterBrand
            )
        else null
    )
}
