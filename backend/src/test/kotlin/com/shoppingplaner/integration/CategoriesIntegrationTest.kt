package com.shoppingplaner.integration

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CategoriesIntegrationTest {

    @Autowired
    lateinit var mvc: MockMvc

    private val auth = "Bearer dev-test-token"

    @Test
    fun `GET categories returns seeded category list`() {
        mvc.get("/api/v2/categories") {
            header("Authorization", auth)
        }.andExpect {
            status { isOk() }
            jsonPath("$") { isArray() }
            jsonPath("$[0].slug") { isString() }
            jsonPath("$[0].icon") { exists() }
        }
    }

    @Test
    fun `GET categories dairy returns category info`() {
        mvc.get("/api/v2/categories/dairy") {
            header("Authorization", auth)
        }.andExpect {
            status { isOk() }
            jsonPath("$.slug") { value("dairy") }
            jsonPath("$.name") { value("Milch & Käse") }
            jsonPath("$.name_en") { value("Dairy") }
        }
    }

    @Test
    fun `GET categories unknown slug returns 404`() {
        mvc.get("/api/v2/categories/does-not-exist") {
            header("Authorization", auth)
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `GET categories dairy products returns array`() {
        mvc.get("/api/v2/categories/dairy/products") {
            header("Authorization", auth)
        }.andExpect {
            status { isOk() }
            jsonPath("$") { isArray() }
        }
    }
}
