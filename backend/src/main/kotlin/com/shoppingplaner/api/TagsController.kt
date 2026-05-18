package com.shoppingplaner.api

import com.shoppingplaner.dto.TagV2Dto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Tags v2", description = "Product filter tags")
@RestController
@RequestMapping("/api/v2/tags")
class TagsController {

    @Operation(summary = "List all available product tags")
    @GetMapping
    fun getTags(): List<TagV2Dto> = TAGS

    companion object {
        val TAGS = listOf(
            TagV2Dto("lactose-free",  "Laktosefrei",      "dietary"),
            TagV2Dto("vegan",         "Vegan",             "dietary"),
            TagV2Dto("vegetarian",    "Vegetarisch",       "dietary"),
            TagV2Dto("bio",           "Bio",               "dietary"),
            TagV2Dto("organic",       "Organisch",         "dietary"),
            TagV2Dto("halal",         "Halal",             "dietary"),
            TagV2Dto("kosher",        "Koscher",           "dietary"),
            TagV2Dto("gluten-free",   "Glutenfrei",        "dietary"),
            TagV2Dto("dairy",         "Milchprodukte",     "category"),
            TagV2Dto("bakery",        "Backwaren",         "category"),
            TagV2Dto("meat",          "Fleisch",           "category"),
            TagV2Dto("produce",       "Obst & Gemüse",     "category"),
            TagV2Dto("beverages",     "Getränke",          "category"),
            TagV2Dto("frozen",        "Tiefkühl",          "category")
        )
    }
}
