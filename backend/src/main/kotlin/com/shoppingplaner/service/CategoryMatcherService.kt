package com.shoppingplaner.service

import com.shoppingplaner.model.Category
import com.shoppingplaner.repository.CategoryRepository
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class CategoryMatcherService(private val categoryRepo: CategoryRepository) {

    // Slugs match the V1__baseline.sql seed data.
    // Order matters — first match wins (frozen before produce, dairy before pantry).
    private val RULES: List<Pair<Regex, String>> = listOf(
        Regex("milch|butter|joghurt|käse|sahne|quark|kefir|mozzarella|schlagsahne|buttermilch|molke|frischkäse|mascarpone|gouda|cheddar|emmental|brie|camembert", RegexOption.IGNORE_CASE) to "dairy",
        Regex("fleisch|huhn|hähnchen|schwein|rind|lachs|fisch|wurst|salami|schinken|schnitzel|steak|putenbrust|thunfisch|garnele|hackfleisch", RegexOption.IGNORE_CASE) to "meat",
        Regex("tiefkühl|tk[-\\s]|gefroren|fischstäbchen|nugget|stäbchen|pommes|tiefkühlpizza", RegexOption.IGNORE_CASE) to "frozen",
        Regex("apfel|tomate|banane|erdbeere|paprika|gurke|kartoffel|möhre|karotte|zwiebel|gemüse|salat|zucchini|brokkoli|spinat|mango|avocado|zitrone|pfirsich|kirsche|birne|traube|melone|orange|mandarine|kiwi|ananas|obst", RegexOption.IGNORE_CASE) to "produce",
        Regex("brot|brötchen|toast|croissant|kuchen|muffin|baguette|vollkorn|brezel|ciabatta|hefezopf|pumpernickel|roggenbrot|weißbrot", RegexOption.IGNORE_CASE) to "bakery",
        Regex("wasser|saft|cola|tee|kaffee|bier|wein|limonade|fanta|sprite|energy.?drink|smoothie|kakao|mineralwasser|sprudel", RegexOption.IGNORE_CASE) to "drinks",
        Regex("chip|schokolade|gummibär|weingummi|keks|riegel|nuss|mandel|popcorn|lakritz|bonbon|praline|waffel|erdnuss", RegexOption.IGNORE_CASE) to "sweets",
        Regex("spülmittel|waschpulver|küchenpapier|toilettenpapier|schwamm|reiniger|spüli|putzmittel|müllbeutel|shampoo|duschgel|deo|zahnpasta|körperpflege|lotion|conditioner|deodorant", RegexOption.IGNORE_CASE) to "home",
        Regex("baby|windel|säugling|kindermilch|babybrei|babynahrung", RegexOption.IGNORE_CASE) to "baby",
        Regex("müsli|haferflocken|cornflakes|marmelade|honig|aufschnitt", RegexOption.IGNORE_CASE) to "breakfast",
        Regex("nudel|pasta|reis|mehl|zucker|öl|essig|konserve|bohne|linse|erbse|soße|ketchup|senf|mayonnaise|salz|pfeffer|gewürz|backpulver", RegexOption.IGNORE_CASE) to "pantry",
    )

    private val cache: ConcurrentHashMap<String, Category?> = ConcurrentHashMap()

    fun match(productName: String): Category? {
        for ((pattern, slug) in RULES) {
            if (pattern.containsMatchIn(productName)) {
                return cache.computeIfAbsent(slug) { categoryRepo.findBySlug(it) }
            }
        }
        return null
    }
}
