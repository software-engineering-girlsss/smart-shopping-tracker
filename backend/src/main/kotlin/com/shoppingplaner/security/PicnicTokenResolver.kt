package com.shoppingplaner.security

import com.shoppingplaner.service.PicnicService
import com.shoppingplaner.model.SearchQuery
import com.shoppingplaner.model.StoreItem
import org.springframework.stereotype.Component

@Component
class PicnicTokenResolver(private val picnicService: PicnicService) {
    fun search(product: SearchQuery): List<StoreItem> = picnicService.search(product)
}
