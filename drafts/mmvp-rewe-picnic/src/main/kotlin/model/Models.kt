package model

data class Product(
    val name: String,
    val quantity: Double,
    val unit: String
)

data class StoreItem(
    val name: String,
    val price: Double,       // unit price in EUR
    val unit: String,
    val url: String?
)

data class MatchedItem(
    val query: Product,
    val matchedName: String,
    val price: Double,
    val url: String?
)

data class PriceResult(
    val store: String,
    val totalPrice: Double?,   // null if store returned no results
    val currency: String = "EUR",
    val items: List<MatchedItem>
)
