package com.shoppingplaner.repository

import com.shoppingplaner.model.DeliveryRule
import org.springframework.data.jpa.repository.JpaRepository

interface DeliveryRuleRepository : JpaRepository<DeliveryRule, Long> {
    fun findByStoreIdOrderByMinBasketAmountAsc(storeId: String): List<DeliveryRule>
}
