package com.shoppingplaner.repository

import com.shoppingplaner.model.ReweProduct
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface ReweProductRepository : JpaRepository<ReweProduct, Long> {
    fun findByReweId(reweId: String): ReweProduct?
    fun findByCatalogId(catalogId: String): List<ReweProduct>
    fun findByLastUpdatedBefore(cutoff: Instant): List<ReweProduct>
}
