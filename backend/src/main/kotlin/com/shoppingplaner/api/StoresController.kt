package com.shoppingplaner.api

import com.shoppingplaner.dto.StoreV2Dto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Stores v2", description = "Available store listing")
@RestController
@RequestMapping("/api/v2/stores")
class StoresController {

    @Operation(summary = "List all supported stores")
    @GetMapping
    fun getStores(): List<StoreV2Dto> = STORES

    companion object {
        val STORES = listOf(
            StoreV2Dto(
                id                = "rewe",
                name              = "REWE",
                type              = "both",
                color             = "#CC071E",
                supportsOrder     = false,
                supportsDeeplink  = true,
                available         = true
            ),
            StoreV2Dto(
                id                = "picnic",
                name              = "Picnic",
                type              = "online",
                color             = "#5BBF21",
                supportsOrder     = true,
                supportsDeeplink  = false,
                available         = true
            )
        )
    }
}
