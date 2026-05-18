package com.shoppingplaner.repository

import com.shoppingplaner.model.PicnicProduct
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface PicnicProductRepository : JpaRepository<PicnicProduct, Long> {
    fun findByPicnicId(picnicId: String): PicnicProduct?
    fun findByCatalogId(catalogId: String): List<PicnicProduct>
    fun findByLastUpdatedBefore(cutoff: Instant): List<PicnicProduct>
}
