package com.shoppingplaner.service

import com.shoppingplaner.repository.DeliveryRuleRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ReweDeliveryPriceCalculationService(
    private val reweService: ReweService,
    private val deliveryRuleRepository: DeliveryRuleRepository
) : DeliveryPriceCalculationService {

    private val log = LoggerFactory.getLogger(ReweDeliveryPriceCalculationService::class.java)

    override val store = "rewe"

    override fun calculate(items: List<BasketItem>): DeliveryCostResult {
        val basketTotal = items.sumOf { it.price * it.quantity }

        // Extract REWE article IDs from product URLs ("https://www.rewe.de/p/{id}")
        val positions = items.mapNotNull { item ->
            val id = item.productId?.trimEnd('/')?.substringAfterLast('/') ?: return@mapNotNull null
            id to item.quantity
        }

        val apiResult = if (positions.isNotEmpty()) {
            reweService.fetchBasketFees(positions)
        } else null

        return if (apiResult != null) {
            log.debug("REWE delivery: live API result — fee={}€, minOrder={}€, remainingForTier={}€",
                apiResult.serviceFee, apiResult.minimumOrderAmount, apiResult.remainingForNextTier)
            buildResult(
                basketTotal       = basketTotal,
                fee               = apiResult.serviceFee,
                minimumOrder      = apiResult.minimumOrderAmount,
                freeThreshold     = apiResult.minimumOrderAmount,
                remainingForTier  = apiResult.remainingForNextTier
            )
        } else {
            log.debug("REWE delivery: falling back to DB rules for basketTotal={}€", basketTotal)
            calculateFromRules(basketTotal)
        }
    }

    private fun calculateFromRules(basketTotal: Double): DeliveryCostResult {
        val rules = deliveryRuleRepository.findByStoreIdOrderByMinBasketAmountAsc("rewe")
        val rule = rules.firstOrNull { r ->
            val aboveMin = r.minBasketAmount == null || basketTotal >= r.minBasketAmount
            val belowMax = r.maxBasketAmount == null || basketTotal < r.maxBasketAmount
            aboveMin && belowMax
        }

        val fee = rule?.deliveryFee ?: 3.90
        val minimumOrder = rules.firstOrNull()?.minimumOrderAmount
        val freeThreshold = rule?.freeDeliveryThreshold ?: rules.maxByOrNull { it.minBasketAmount ?: 0.0 }?.minBasketAmount

        return buildResult(
            basketTotal       = basketTotal,
            fee               = fee,
            minimumOrder      = minimumOrder,
            freeThreshold     = freeThreshold,
            remainingForTier  = null
        )
    }

    private fun buildResult(
        basketTotal: Double,
        fee: Double,
        minimumOrder: Double?,
        freeThreshold: Double?,
        remainingForTier: Double?
    ): DeliveryCostResult {
        val isFree = fee == 0.0
        val amountToFree = when {
            isFree -> null
            freeThreshold != null && basketTotal < freeThreshold -> freeThreshold - basketTotal
            remainingForTier != null && remainingForTier > 0 -> remainingForTier
            else -> null
        }
        val hint = buildHint(isFree, fee, amountToFree, minimumOrder, basketTotal)
        return DeliveryCostResult(
            store                = store,
            fee                  = fee,
            isFree               = isFree,
            basketTotal          = basketTotal,
            minimumOrderAmount   = minimumOrder,
            freeDeliveryThreshold = freeThreshold,
            amountToFreeDelivery = amountToFree?.let { "%.2f".format(it).toDouble() },
            hint                 = hint
        )
    }

    private fun buildHint(
        isFree: Boolean,
        fee: Double,
        amountToFree: Double?,
        minimumOrder: Double?,
        basketTotal: Double
    ): String? = when {
        minimumOrder != null && basketTotal < minimumOrder ->
            "Mindestbestellwert: %.2f €".format(minimumOrder)
        amountToFree != null && amountToFree > 0 ->
            "Noch %.2f € bis zur kostenlosen Lieferung".format(amountToFree)
        isFree -> "Kostenlose Lieferung"
        else -> "Liefergebühr: %.2f €".format(fee)
    }
}
