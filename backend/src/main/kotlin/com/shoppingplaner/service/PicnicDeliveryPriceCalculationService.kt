package com.shoppingplaner.service

import com.shoppingplaner.repository.DeliveryRuleRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PicnicDeliveryPriceCalculationService(
    private val picnicService: PicnicService,
    private val deliveryRuleRepository: DeliveryRuleRepository
) : DeliveryPriceCalculationService {

    private val log = LoggerFactory.getLogger(PicnicDeliveryPriceCalculationService::class.java)

    override val store = "picnic"

    override fun calculate(items: List<BasketItem>): DeliveryCostResult {
        val basketTotal = items.sumOf { it.price * it.quantity }

        val apiInfo = picnicService.fetchDeliveryInfo()

        return if (apiInfo != null) {
            log.debug("Picnic delivery: live API result — fee={}€, minOrder={}€",
                apiInfo.deliveryFee, apiInfo.minimumOrderAmount)
            buildResult(
                basketTotal  = basketTotal,
                fee          = apiInfo.deliveryFee,
                minimumOrder = apiInfo.minimumOrderAmount,
                freeThreshold = apiInfo.minimumOrderAmount
            )
        } else {
            log.debug("Picnic delivery: falling back to DB rules for basketTotal={}€", basketTotal)
            calculateFromRules(basketTotal)
        }
    }

    private fun calculateFromRules(basketTotal: Double): DeliveryCostResult {
        val rules = deliveryRuleRepository.findByStoreIdOrderByMinBasketAmountAsc("picnic")
        val rule = rules.firstOrNull { r ->
            val aboveMin = r.minBasketAmount == null || basketTotal >= r.minBasketAmount
            val belowMax = r.maxBasketAmount == null || basketTotal < r.maxBasketAmount
            aboveMin && belowMax
        }

        val fee = rule?.deliveryFee ?: 0.0
        val minimumOrder = rules.firstOrNull()?.minimumOrderAmount
        val freeThreshold = rule?.freeDeliveryThreshold

        return buildResult(
            basketTotal   = basketTotal,
            fee           = fee,
            minimumOrder  = minimumOrder,
            freeThreshold = freeThreshold
        )
    }

    private fun buildResult(
        basketTotal: Double,
        fee: Double,
        minimumOrder: Double?,
        freeThreshold: Double?
    ): DeliveryCostResult {
        val isFree = fee == 0.0
        val amountToFree = when {
            !isFree && freeThreshold != null && basketTotal < freeThreshold -> freeThreshold - basketTotal
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
