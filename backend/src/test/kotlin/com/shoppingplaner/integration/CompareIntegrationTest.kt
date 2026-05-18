package com.shoppingplaner.integration

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CompareIntegrationTest {

    @Autowired
    lateinit var mvc: MockMvc

    private val authHeader = "Bearer dev-test-token"

    @Test
    fun `health endpoint returns UP`() {
        mvc.get("/api/v1/health") {
            header("Authorization", authHeader)
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UP") }
        }
    }

    @Test
    fun `compare returns response with both stores`() {
        mvc.post("/api/v1/compare") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = """{"products":[{"name":"Milch","quantity":1,"unit":"liter"}]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.stores.length()") { value(2) }
            jsonPath("$.stores[0].store") { isNotEmpty() }
            jsonPath("$.stores[0].items.length()") { value(1) }
        }
    }

    @Test
    fun `search returns results for both stores`() {
        mvc.post("/api/v1/search") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"Butter","quantity":1,"unit":"stk"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.results.length()") { value(2) }
            jsonPath("$.results[0].store") { value("REWE") }
            jsonPath("$.results[1].store") { value("Picnic") }
        }
    }

    @Test
    fun `compare with empty products returns graceful response`() {
        mvc.post("/api/v1/compare") {
            header("Authorization", authHeader)
            contentType = MediaType.APPLICATION_JSON
            content = """{"products":[]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.stores.length()") { value(2) }
        }
    }

    @Test
    fun `request without auth returns 401`() {
        mvc.post("/api/v1/search") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"Milch","quantity":1,"unit":"stk"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }
}
