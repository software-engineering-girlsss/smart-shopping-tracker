package com.shoppingplaner.service

import com.shoppingplaner.model.ReweProduct
import com.shoppingplaner.model.PicnicProduct
import com.shoppingplaner.model.SearchQuery
import com.shoppingplaner.repository.ReweProductRepository
import com.shoppingplaner.repository.PicnicProductRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class ProductRefreshService(
    private val reweService: ReweService,
    private val picnicService: PicnicService,
    private val reweProductRepo: ReweProductRepository,
    private val picnicProductRepo: PicnicProductRepository,
) {
    private val log = LoggerFactory.getLogger(ProductRefreshService::class.java)

    @Transactional
    fun refreshReweProduct(reweProductId: Long): ReweProduct {
        val reweProduct = reweProductRepo.findById(reweProductId)
            .orElseThrow { NoSuchElementException("ReweProduct $reweProductId not found") }

        val query = SearchQuery(reweProduct.catalog.name, reweProduct.catalog.quantityAmount, reweProduct.catalog.quantityUnit)
        val results = reweService.search(query)

        val match = results.firstOrNull { item ->
            item.url?.let { url ->
                url.contains(reweProduct.reweId) ||
                Regex("""/p/(\d+)""").find(url)?.groupValues?.get(1) == reweProduct.reweId
            } ?: false
        } ?: results.firstOrNull()

        val updated = reweProduct.copy(
            lastPrice = match?.price ?: reweProduct.lastPrice,
            available = match != null,
            lastUpdated = Instant.now()
        )
        log.debug("Refreshed REWE product {} ({}): price={}, available={}", reweProductId, reweProduct.reweId, updated.lastPrice, updated.available)
        return reweProductRepo.save(updated)
    }

    @Transactional
    fun refreshPicnicProduct(picnicProductId: Long): PicnicProduct {
        val picnicProduct = picnicProductRepo.findById(picnicProductId)
            .orElseThrow { NoSuchElementException("PicnicProduct $picnicProductId not found") }

        val query = SearchQuery(picnicProduct.catalog.name, picnicProduct.catalog.quantityAmount, picnicProduct.catalog.quantityUnit)
        val results = picnicService.search(query)

        val match = results.firstOrNull { item ->
            item.url?.trimEnd('/')?.substringAfterLast('/') == picnicProduct.picnicId
        } ?: results.firstOrNull()

        val updated = picnicProduct.copy(
            lastPrice = match?.price ?: picnicProduct.lastPrice,
            available = match != null,
            lastUpdated = Instant.now()
        )
        log.debug("Refreshed Picnic product {} ({}): price={}, available={}", picnicProductId, picnicProduct.picnicId, updated.lastPrice, updated.available)
        return picnicProductRepo.save(updated)
    }

    fun refreshAllStaleProducts(olderThanHours: Int = 24): Map<String, Int> {
        val cutoff = Instant.now().minus(olderThanHours.toLong(), ChronoUnit.HOURS)

        val staleRewe = reweProductRepo.findByLastUpdatedBefore(cutoff)
        val stalePicnic = picnicProductRepo.findByLastUpdatedBefore(cutoff)

        var reweRefreshed = 0
        var picnicRefreshed = 0

        staleRewe.forEach { rp ->
            runCatching { refreshReweProduct(rp.id!!) }.onSuccess { reweRefreshed++ }
                .onFailure { log.warn("Failed to refresh REWE product {}: {}", rp.id, it.message) }
        }
        stalePicnic.forEach { pp ->
            runCatching { refreshPicnicProduct(pp.id!!) }.onSuccess { picnicRefreshed++ }
                .onFailure { log.warn("Failed to refresh Picnic product {}: {}", pp.id, it.message) }
        }

        log.info("Stale product refresh: {} REWE, {} Picnic", reweRefreshed, picnicRefreshed)
        return mapOf("rewe" to reweRefreshed, "picnic" to picnicRefreshed)
    }
}
