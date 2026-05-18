package com.shoppingplaner

import com.shoppingplaner.config.AppProperties
import io.github.cdimascio.dotenv.dotenv
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(AppProperties::class)
@EnableScheduling
class ShoppingPlanerApplication

fun main(args: Array<String>) {
    val env = dotenv {
        ignoreIfMissing = true
        ignoreIfMalformed = true
    }
    runApplication<ShoppingPlanerApplication>(*args) {
        setDefaultProperties(env.entries().associate { it.key to it.value })
    }
}
