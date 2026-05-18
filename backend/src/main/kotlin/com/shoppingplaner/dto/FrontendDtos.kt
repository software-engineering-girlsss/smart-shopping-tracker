package com.shoppingplaner.dto

import com.fasterxml.jackson.annotation.JsonProperty

// Mirrors frontend/types.ts exactly so the web SPA can talk to the backend without changes.

enum class StoreId(val displayName: String, val type: String, val color: String) {
    rewe("REWE",          "offline", "#CC071E"),
    rewe_online("REWE Lieferservice", "online", "#CC071E"),
    picnic("Picnic",      "online",  "#5BBF21"),
    aldi("Aldi",          "offline", "#009EE0"),
    lidl("Lidl",          "offline", "#EFE21A"),
    netto("Netto",        "offline", "#F90000"),
    kaufland("Kaufland",  "offline", "#CC0000");
}

data class StorePriceDto(
    val store: String,
    val price: Double,
    val unit: String,
    val available: Boolean
)

data class BestPriceDto(
    val store: String,
    val price: Double
)

data class FrontendProductDto(
    val id: String,
    val name: String,
    @JsonProperty("normalized_name") val normalizedName: String,
    val brand: String = "",
    @JsonProperty("image_url") val imageUrl: String = "",
    val category: String = "",
    val prices: List<StorePriceDto>,
    @JsonProperty("best_price") val bestPrice: BestPriceDto,
    val score: Double? = null,
    @JsonProperty("is_favorite") val isFavorite: Boolean = false,
    val promotion: FrontendPromotionDto? = null
)

data class FrontendSearchResponse(
    val items: List<FrontendProductDto>,
    val total: Int,
    val page: Int
)

data class FrontendCartItemDto(
    val id: String,
    @JsonProperty("product_id") val productId: String,
    val name: String,
    @JsonProperty("image_url") val imageUrl: String = "",
    val quantity: Int,
    val unit: String,
    @JsonProperty("checked_off") val checkedOff: Boolean = false
)

data class FrontendCartDto(
    @JsonProperty("cart_id") val cartId: String,
    val items: List<FrontendCartItemDto>,
    @JsonProperty("updated_at") val updatedAt: String
)

data class AddToCartRequest(
    @JsonProperty("product_id") val productId: String,
    val quantity: Int = 1
)

data class UpdateCartItemRequest(
    @JsonProperty("checked_off") val checkedOff: Boolean? = null,
    val quantity: Int? = null
)

data class StoreCartTotalDto(
    val store: String,
    val name: String,
    val type: String,
    val total: Double,
    @JsonProperty("available_items") val availableItems: Int,
    @JsonProperty("missing_items") val missingItems: Int,
    val deeplink: String? = null,
    val color: String
)

data class CartPriceComparisonDto(
    @JsonProperty("cart_id") val cartId: String,
    val stores: List<StoreCartTotalDto>,
    @JsonProperty("savings_vs_most_expensive") val savingsVsMostExpensive: Double
)

data class FrontendPromotionDto(
    val id: String,
    @JsonProperty("product_id") val productId: String,
    val store: String,
    @JsonProperty("discount_percent") val discountPercent: Int,
    @JsonProperty("original_price") val originalPrice: Double,
    @JsonProperty("promo_price") val promoPrice: Double,
    @JsonProperty("valid_until") val validUntil: String,
    val badge: String
)

data class RecipeIngredientDto(
    @JsonProperty("product_id") val productId: String,
    val name: String,
    val amount: Double,
    val unit: String
)

data class RecipeDto(
    val id: String,
    val name: String,
    @JsonProperty("image_url") val imageUrl: String = "",
    @JsonProperty("duration_min") val durationMin: Int = 30,
    val servings: Int = 2,
    val category: String = "",
    val ingredients: List<RecipeIngredientDto> = emptyList()
)

data class AddRecipeToCartRequest(
    @JsonProperty("cart_id") val cartId: String,
    val servings: Int = 2
)
