package com.shoppingplaner.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

// ── Auth v2 ──────────────────────────────────────────────────────────────────

data class RegisterRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String,
    @field:NotBlank val name: String
)

data class VerifyEmailRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val code: String
)

data class ResendCodeRequest(
    @field:Email @field:NotBlank val email: String
)

data class PendingVerificationResponse(
    @JsonProperty("pending_verification") val pendingVerification: Boolean = true,
    val email: String
)

data class LoginRequest(
    @field:NotBlank val email: String,
    @field:NotBlank val password: String
)

data class InviteAuthRequest(
    @JsonProperty("invite_token") val inviteToken: String,
    val email: String? = null,
    val password: String? = null
)

data class RefreshTokenRequest(
    @JsonProperty("refresh_token") val refreshToken: String
)

data class ConnectedAccountDto(
    val provider: String = "picnic",
    val email: String,
    @JsonProperty("connected_at") val connectedAt: String,
    @JsonProperty("expires_at") val expiresAt: Long?,
    @JsonProperty("zip_code") val zipCode: String? = null
)

data class UserV2Dto(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
    @JsonProperty("connected_accounts") val connectedAccounts: List<ConnectedAccountDto> = emptyList(),
    @JsonProperty("created_at") val createdAt: String? = null
)

data class AuthResponse(
    val user: UserV2Dto,
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("refresh_token") val refreshToken: String
)

data class PatchMeRequest(
    val name: String? = null,
    val email: String? = null
)

data class ConnectPicnicRequest(
    val email: String,
    val password: String,
    @JsonProperty("zip_code") val zipCode: String? = null
)

/** Returned when Picnic requires 2FA before the connection can be completed. */
data class PicnicNeeds2FADto(
    @JsonProperty("needs_2fa") val needs2fa: Boolean = true,
    val email: String,
    val message: String = "Enter the verification code sent to your registered 2FA method"
)

data class Verify2FARequest(val otp: String)

data class PushTokenRequest(
    val token: String,
    val platform: String  // "ios" | "android"
)

// ── Stores v2 ────────────────────────────────────────────────────────────────

data class StoreV2Dto(
    val id: String,
    val name: String,
    val type: String,
    val color: String,
    @JsonProperty("logo_url") val logoUrl: String = "",
    @JsonProperty("supports_order") val supportsOrder: Boolean = false,
    @JsonProperty("supports_deeplink") val supportsDeeplink: Boolean = false,
    val available: Boolean = true
)

// ── Tags v2 ──────────────────────────────────────────────────────────────────

data class TagV2Dto(
    val id: String,
    val name: String,
    val category: String
)

// ── Products v2 ──────────────────────────────────────────────────────────────

data class PromotionV2Dto(
    val description: String,
    @JsonProperty("original_price") val originalPrice: Double,
    @JsonProperty("valid_until") val validUntil: String
)

data class StorePriceV2Dto(
    val store: String,
    val price: Double,
    val unit: String,
    val available: Boolean = true,
    val promotion: PromotionV2Dto? = null
)

data class BestPriceV2Dto(
    val store: String,
    val price: Double
)

data class MatchScoreDto(
    val score: Double,
    /** "exact" | "high" | "partial" | "low" */
    val level: String,
    val explanation: String? = null,
    @JsonProperty("is_bundle_suggestion") val isBundleSuggestion: Boolean = false,
    @JsonProperty("bundle_note") val bundleNote: String? = null
)

data class UnifiedProductDto(
    val id: String,
    val name: String,
    val brand: String = "",
    @JsonProperty("image_url") val imageUrl: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    val prices: List<StorePriceV2Dto>,
    @JsonProperty("best_price") val bestPrice: BestPriceV2Dto,
    val match: MatchScoreDto? = null
)

data class TagFilterDto(val id: String, val name: String, val count: Int)
data class StoreFilterDto(val id: String, val name: String, val count: Int)
data class PriceRangeDto(val min: Double, val max: Double)

data class AvailableFiltersDto(
    val tags: List<TagFilterDto> = emptyList(),
    val stores: List<StoreFilterDto> = emptyList(),
    @JsonProperty("price_range") val priceRange: PriceRangeDto? = null
)

data class ProductsPageResponseV2(
    val items: List<UnifiedProductDto>,
    val total: Int,
    val page: Int,
    @JsonProperty("available_filters") val availableFilters: AvailableFiltersDto? = null
)

// ── Cart v2 ──────────────────────────────────────────────────────────────────

data class CartItemFiltersDto(
    val tags: List<String>? = null,
    val volume: String? = null,
    @JsonProperty("fat_content") val fatContent: String? = null,
    val brand: String? = null
)

data class ResolvedCartItemDto(
    val product: UnifiedProductDto?,
    val match: MatchScoreDto?
)

data class StoreSelectionDto(
    val store: String,
    @JsonProperty("product_id") val productId: String? = null,
    val name: String,
    @JsonProperty("image_url") val imageUrl: String? = null,
    val price: Double? = null
)

data class CartItemV2Dto(
    val id: String,
    val query: String,
    val quantity: Int,
    val filters: CartItemFiltersDto? = null,
    val resolved: ResolvedCartItemDto? = null,
    @JsonProperty("store_selections") val storeSelections: Map<String, StoreSelectionDto>? = null
)

data class CartV2Dto(
    val id: String,
    @JsonProperty("user_id") val userId: String,
    val items: List<CartItemV2Dto>,
    @JsonProperty("updated_at") val updatedAt: String
)

data class AddCartItemRequest(
    /** Free-text product query (mutually exclusive with product_id) */
    val query: String? = null,
    /** Specific product ID from a store (e.g. "rewe:12345") */
    @JsonProperty("product_id") val productId: String? = null,
    val quantity: Int = 1,
    val filters: CartItemFiltersDto? = null,
    /** Optional link to canonical catalog product */
    @JsonProperty("catalog_product_id") val catalogProductId: String? = null
)

data class UpdateCartItemV2Request(
    val quantity: Int? = null,
    val filters: CartItemFiltersDto? = null,
    @JsonProperty("store_selection") val storeSelection: StoreSelectionDto? = null
)

// ── Comparison v2 ────────────────────────────────────────────────────────────

data class ComparisonItemV2Dto(
    @JsonProperty("cart_item_id") val cartItemId: String,
    val product: UnifiedProductDto?,
    val match: MatchScoreDto?,
    /** "available" | "partial_match" | "substituted" | "missing" */
    val status: String,
    @JsonProperty("alternatives_count") val alternativesCount: Int = 0
)

data class MissingItemV2Dto(
    @JsonProperty("cart_item_id") val cartItemId: String,
    val query: String,
    val suggestion: String? = null,
    @JsonProperty("alternatives_count") val alternativesCount: Int = 0
)

data class DeliveryCostDto(
    val fee: Double,
    @JsonProperty("is_free") val isFree: Boolean,
    @JsonProperty("minimum_order") val minimumOrder: Double?,
    @JsonProperty("free_threshold") val freeThreshold: Double?,
    @JsonProperty("amount_to_free") val amountToFree: Double?,
    val hint: String?
)

data class StoreComparisonResultV2Dto(
    val store: String,
    @JsonProperty("store_name") val storeName: String,
    val total: Double,
    @JsonProperty("total_with_delivery") val totalWithDelivery: Double,
    val currency: String = "EUR",
    @JsonProperty("available_items") val availableItems: Int,
    @JsonProperty("missing_items") val missingItems: Int,
    val items: List<ComparisonItemV2Dto>,
    val missing: List<MissingItemV2Dto> = emptyList(),
    val delivery: DeliveryCostDto? = null
)

data class ComparisonSummaryDto(
    @JsonProperty("cheapest_store") val cheapestStore: String?,
    @JsonProperty("most_expensive_store") val mostExpensiveStore: String?,
    @JsonProperty("max_savings") val maxSavings: Double?
)

data class CartComparisonResponseV2(
    @JsonProperty("cart_id") val cartId: String,
    val stores: List<StoreComparisonResultV2Dto>,
    val summary: ComparisonSummaryDto
)

data class AlternativeItemDto(
    val product: UnifiedProductDto,
    val match: MatchScoreDto,
    @JsonProperty("is_bundle_suggestion") val isBundleSuggestion: Boolean = false
)

data class AlternativesResponseDto(val alternatives: List<AlternativeItemDto>)

data class OrderResponseDto(
    /** "deeplink" or "api_order" */
    val type: String,
    val deeplink: String? = null,
    @JsonProperty("order_id") val orderId: String? = null,
    @JsonProperty("redirect_url") val redirectUrl: String? = null
)

data class ShareRequest(
    /** "link" | "text" | "pdf" */
    val format: String
)

data class ShareResponseDto(
    val link: String? = null,
    val text: String? = null,
    @JsonProperty("pdf_url") val pdfUrl: String? = null
)

// ── Favorites v2 ─────────────────────────────────────────────────────────────

data class FavoriteV2Dto(
    val id: String,
    /** "specific" | "generic" */
    val type: String,
    @JsonProperty("product_id") val productId: String? = null,
    val query: String? = null,
    val filters: CartItemFiltersDto? = null
)

data class AddFavoriteRequest(
    /** "specific" | "generic" */
    val type: String,
    @JsonProperty("product_id") val productId: String? = null,
    val query: String? = null,
    val filters: CartItemFiltersDto? = null
)

data class DealInfoDto(
    val store: String,
    @JsonProperty("original_price") val originalPrice: Double,
    @JsonProperty("current_price") val currentPrice: Double,
    @JsonProperty("discount_percent") val discountPercent: Int,
    @JsonProperty("valid_until") val validUntil: String
)

data class FavoriteDealDto(
    val favorite: FavoriteV2Dto,
    val deal: DealInfoDto
)

// ── Receipts v2 ──────────────────────────────────────────────────────────────

data class ReceiptItemDto(
    val id: String,
    @JsonProperty("raw_text") val rawText: String,
    @JsonProperty("normalized_name") val normalizedName: String,
    val quantity: Int,
    @JsonProperty("paid_price") val paidPrice: Double,
    @JsonProperty("matched_product") val matchedProduct: UnifiedProductDto? = null
)

data class ReceiptScanResponseDto(
    @JsonProperty("receipt_id") val receiptId: String,
    /** "processing" | "done" | "failed" */
    val status: String,
    @JsonProperty("store_detected") val storeDetected: String? = null,
    val items: List<ReceiptItemDto> = emptyList(),
    @JsonProperty("total_paid") val totalPaid: Double? = null
)

data class AddReceiptToCartRequest(
    @JsonProperty("item_ids") val itemIds: List<String>? = null
)

data class AddReceiptToCartResponseDto(
    val cart: CartV2Dto,
    @JsonProperty("added_items") val addedItems: List<CartItemV2Dto>
)

data class ReceiptStoreAlternativeDto(
    val store: String,
    val total: Double,
    val savings: Double
)

data class ReceiptSavingsDto(
    @JsonProperty("total_paid") val totalPaid: Double,
    @JsonProperty("store_detected") val storeDetected: String?,
    val alternatives: List<ReceiptStoreAlternativeDto>
)

// ── Admin v2 ─────────────────────────────────────────────────────────────────

data class AdminUserDto(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
    val blocked: Boolean = false,
    @JsonProperty("created_at") val createdAt: String? = null
)

data class PatchAdminUserRequest(
    val role: String? = null,
    val blocked: Boolean? = null
)

data class InviteTokenResponseDto(
    val token: String,
    @JsonProperty("expires_at") val expiresAt: Long
)

data class AdminStoreDto(
    val id: String,
    val name: String,
    val available: Boolean
)

data class PatchStoreRequest(
    val available: Boolean? = null
)

data class AdminTagDto(
    val id: String,
    val name: String,
    val category: String
)

data class CreateTagRequest(
    val id: String,
    val name: String,
    val category: String
)
