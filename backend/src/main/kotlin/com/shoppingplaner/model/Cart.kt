package com.shoppingplaner.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "carts", uniqueConstraints = [UniqueConstraint(columnNames = ["user_id"])])
data class Cart(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val name: String,

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, mappedBy = "cart", fetch = FetchType.LAZY)
    val items: MutableList<CartItem> = mutableListOf(),

    val userId: String? = null,

    val createdAt: Instant = Instant.now()
)
