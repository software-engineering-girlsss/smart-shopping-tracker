package com.shoppingplaner.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {

    @GetMapping("/api/v2/health")
    fun healthV2() = mapOf("status" to "UP", "version" to "v2", "stores" to listOf("REWE", "Picnic"))

    @GetMapping("/api/v1/health")
    fun healthV1() = mapOf("status" to "UP", "stores" to listOf("REWE", "Picnic"))
}
