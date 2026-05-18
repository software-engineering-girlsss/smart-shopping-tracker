package com.shoppingplaner.integration

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class LogoutTimingTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `logout returns immediately regardless of Supabase RTT`() {
        // The Supabase revocation call is fire-and-forget (virtual thread).
        // Even if the outbound call takes seconds, the HTTP response must come back fast.
        repeat(3) {
            val start = System.currentTimeMillis()
            mvc.post("/api/v2/auth/logout") {
                header("Authorization", "Bearer dev-test-token")
            }.andExpect {
                status { isOk() }
            }
            val ms = System.currentTimeMillis() - start
            assertTrue(ms < 500, "logout response took ${ms}ms — expected <500ms (fire-and-forget should not block on Supabase RTT)")
        }
    }
}
