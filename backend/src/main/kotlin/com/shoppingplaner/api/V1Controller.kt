package com.shoppingplaner.api

import com.shoppingplaner.dto.*
import com.shoppingplaner.model.SearchQuery
import com.shoppingplaner.security.PRINCIPAL_ATTR
import com.shoppingplaner.security.AuthPrincipal
import com.shoppingplaner.service.PriceComparisonService
import com.shoppingplaner.service.ReweService
import com.shoppingplaner.service.PicnicService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@Tag(name = "V1", description = "Legacy v1 endpoints used by the web frontend")
@RestController
@RequestMapping("/api/v1")
class V1Controller(
    private val comparisonService: PriceComparisonService,
    private val reweService: ReweService,
    private val picnicService: PicnicService
) {
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    @Operation(summary = "Compare prices for a list of products across REWE and Picnic")
    @PostMapping("/compare")
    fun compare(
        @RequestBody request: CompareRequest,
        httpRequest: HttpServletRequest
    ): CompareResponse {
        val picnicToken = (httpRequest.getAttribute(PRINCIPAL_ATTR) as? AuthPrincipal.UserAccess)?.picnicToken
        return comparisonService.compare(request, picnicToken)
    }

    @Operation(summary = "Search for a single product across REWE and Picnic")
    @PostMapping("/search")
    fun search(@RequestBody request: SearchRequest): SearchResponse {
        val product = SearchQuery(request.query, request.quantity, request.unit)

        val reweFuture   = executor.submit(Callable { reweService.search(product) })
        val picnicFuture = executor.submit(Callable { picnicService.search(product) })

        val reweItems   = reweFuture.get()
        val picnicItems = picnicFuture.get()

        return SearchResponse(
            results = listOf(
                StoreResultDto(
                    store = "REWE",
                    items = reweItems.map { StoreItemDto(it.name, it.price, it.unit, it.url) }
                ),
                StoreResultDto(
                    store = "Picnic",
                    items = picnicItems.map { StoreItemDto(it.name, it.price, it.unit, it.url) }
                )
            )
        )
    }
}
