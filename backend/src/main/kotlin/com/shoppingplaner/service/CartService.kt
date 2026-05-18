package com.shoppingplaner.service

import com.shoppingplaner.dto.CartDto
import com.shoppingplaner.dto.CartItemDto
import com.shoppingplaner.dto.CompareRequest
import com.shoppingplaner.dto.CompareResponse
import com.shoppingplaner.dto.ProductDto
import com.shoppingplaner.model.Cart
import com.shoppingplaner.model.CartItem
import com.shoppingplaner.repository.CartRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class CartItemSnapshot(
    val id: Long,
    val name: String,
    val quantity: Double,
    val unit: String,
    val storeSelectionsJson: String?
)

data class CartSnapshot(val cartId: Long, val items: List<CartItemSnapshot>)

@Service
@Transactional
class CartService(
    private val cartRepo: CartRepository,
    private val comparisonService: PriceComparisonService
) {
    fun findAll(): List<CartDto> = cartRepo.findAll().map { it.toDto() }

    fun findById(id: Long): CartDto? = cartRepo.findById(id).orElse(null)?.toDto()

    fun create(dto: CartDto): CartDto {
        val cart = Cart(name = dto.name)
        dto.items.forEach { item ->
            cart.items.add(CartItem(name = item.name, quantity = item.quantity, unit = item.unit, cart = cart))
        }
        return cartRepo.save(cart).toDto()
    }

    fun update(id: Long, dto: CartDto): CartDto? {
        val existing = cartRepo.findById(id).orElse(null) ?: return null
        existing.items.clear()
        dto.items.forEach { item ->
            existing.items.add(CartItem(name = item.name, quantity = item.quantity, unit = item.unit, cart = existing))
        }
        return cartRepo.save(Cart(id = existing.id, name = dto.name, items = existing.items, createdAt = existing.createdAt)).toDto()
    }

    fun delete(id: Long): Boolean {
        if (!cartRepo.existsById(id)) return false
        cartRepo.deleteById(id)
        return true
    }

    fun loadOrCreateCart(userId: String): CartSnapshot {
        val cart = cartRepo.findFirstByUserIdOrderByIdAsc(userId).orElseGet {
            try {
                cartRepo.save(Cart(name = "My Cart", userId = userId))
            } catch (_: org.springframework.dao.DataIntegrityViolationException) {
                cartRepo.findFirstByUserIdOrderByIdAsc(userId).orElseThrow()
            }
        }
        return CartSnapshot(
            cartId = cart.id!!,
            items  = cart.items.map { CartItemSnapshot(it.id!!, it.name, it.quantity, it.unit, it.storeSelectionsJson) }
        )
    }

    fun compareCart(id: Long): CompareResponse? {
        val cart = cartRepo.findById(id).orElse(null) ?: return null
        val request = CompareRequest(
            products = cart.items.map { ProductDto(it.name, it.quantity, it.unit) }
        )
        return comparisonService.compare(request)
    }

    private fun Cart.toDto() = CartDto(
        id        = this.id,
        name      = this.name,
        items     = this.items.map { CartItemDto(it.id, it.name, it.quantity, it.unit) },
        createdAt = this.createdAt.toString()
    )
}
