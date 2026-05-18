package com.shoppingplaner.integration

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CartV2IntegrationTest {

    @Autowired
    lateinit var mvc: MockMvc

    private val auth = "Bearer dev-test-token"

    @BeforeEach
    fun clearCart() {
        mvc.delete("/api/v2/cart") { header("Authorization", auth) }
    }

    @Test
    fun `GET cart returns empty cart initially`() {
        mvc.get("/api/v2/cart") {
            header("Authorization", auth)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { exists() }
            jsonPath("$.items") { isArray() }
            jsonPath("$.items.length()") { value(0) }
        }
    }

    @Test
    fun `POST items adds item and returns it with server id`() {
        mvc.post("/api/v2/cart/items") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"Vollmilch","quantity":2}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { exists() }
            jsonPath("$.query") { value("Vollmilch") }
            jsonPath("$.quantity") { value(2) }
        }
    }

    @Test
    fun `POST items rejects request without query and product_id`() {
        mvc.post("/api/v2/cart/items") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"quantity":1}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `PATCH items updates quantity`() {
        val addResult = mvc.post("/api/v2/cart/items") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"Butter","quantity":1}"""
        }.andReturn()

        val itemId = JsonPath.read<String>(addResult.response.contentAsString, "$.id")

        mvc.patch("/api/v2/cart/items/$itemId") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"quantity":3}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(itemId) }
            jsonPath("$.quantity") { value(3) }
        }
    }

    @Test
    fun `DELETE items removes item`() {
        val addResult = mvc.post("/api/v2/cart/items") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"Eier","quantity":1}"""
        }.andReturn()

        val itemId = JsonPath.read<String>(addResult.response.contentAsString, "$.id")

        mvc.delete("/api/v2/cart/items/$itemId") {
            header("Authorization", auth)
        }.andExpect {
            status { isNoContent() }
        }

        mvc.get("/api/v2/cart") {
            header("Authorization", auth)
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(0) }
        }
    }

    @Test
    fun `DELETE items returns 404 for unknown item`() {
        mvc.delete("/api/v2/cart/items/999999") {
            header("Authorization", auth)
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `DELETE cart clears all items`() {
        mvc.post("/api/v2/cart/items") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"Milch","quantity":1}"""
        }
        mvc.post("/api/v2/cart/items") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"Käse","quantity":2}"""
        }

        mvc.delete("/api/v2/cart") {
            header("Authorization", auth)
        }.andExpect {
            status { isNoContent() }
        }

        mvc.get("/api/v2/cart") {
            header("Authorization", auth)
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(0) }
        }
    }

    @Test
    fun `full CRUD lifecycle works end to end`() {
        // Add two items
        val r1 = mvc.post("/api/v2/cart/items") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"Joghurt","quantity":1}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { exists() }
        }.andReturn()

        val id1 = JsonPath.read<String>(r1.response.contentAsString, "$.id")

        mvc.post("/api/v2/cart/items") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"Orangensaft","quantity":3}"""
        }.andExpect { status { isCreated() } }

        // Cart should have 2 items
        mvc.get("/api/v2/cart") {
            header("Authorization", auth)
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(2) }
        }

        // Update first item
        mvc.patch("/api/v2/cart/items/$id1") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"quantity":4}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.quantity") { value(4) }
        }

        // Remove first item
        mvc.delete("/api/v2/cart/items/$id1") {
            header("Authorization", auth)
        }.andExpect { status { isNoContent() } }

        // Only second item should remain
        mvc.get("/api/v2/cart") {
            header("Authorization", auth)
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(1) }
            jsonPath("$.items[0].query") { value("Orangensaft") }
        }
    }

    @Test
    fun `unauthenticated requests return 401`() {
        mvc.get("/api/v2/cart").andExpect { status { isUnauthorized() } }
        mvc.post("/api/v2/cart/items") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"Milch","quantity":1}"""
        }.andExpect { status { isUnauthorized() } }
    }
}
