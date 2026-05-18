package com.shoppingplaner.model

import jakarta.persistence.*

@Entity
@Table(name = "categories")
data class Category(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val name: String,

    @Column(name = "name_en")
    val nameEn: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    val parent: Category? = null,

    val slug: String,

    val icon: String? = null,

    @Column(name = "sort_order")
    val sortOrder: Int = 0
)
