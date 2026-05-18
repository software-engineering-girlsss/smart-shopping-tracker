package com.shoppingplaner.integration

import com.shoppingplaner.repository.PicnicConnectionRepository
import com.shoppingplaner.service.PicnicLoginResult
import com.shoppingplaner.service.PicnicService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@TestPropertySource(properties = ["spring.cache.type=none"])
class UsersV2IntegrationTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var picnicConnectionRepo: PicnicConnectionRepository

    @MockBean
    lateinit var picnicService: PicnicService

    private val auth = "Bearer dev-test-token"

    @BeforeEach
    fun cleanup() {
        picnicConnectionRepo.deleteAll()
    }

    // ── GET /me ───────────────────────────────────────────────────────────────

    @Test
    fun `GET me returns 200 with empty connected_accounts when not linked`() {
        mvc.get("/api/v2/users/me") {
            header("Authorization", auth)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value("dev-user-id") }
            jsonPath("$.email") { value("dev@test.local") }
            jsonPath("$.connected_accounts") { isArray() }
            jsonPath("$.connected_accounts.length()") { value(0) }
        }
    }

    @Test
    fun `GET me returns 401 without auth`() {
        mvc.get("/api/v2/users/me").andExpect {
            status { isUnauthorized() }
        }
    }

    // ── POST /me/accounts/picnic — no 2FA ─────────────────────────────────────

    @Test
    fun `POST connect picnic returns 401 when credentials are invalid`() {
        given(picnicService.loginResult("bad@email.com", "wrongpass"))
            .willReturn(PicnicLoginResult.Failed)

        mvc.post("/api/v2/users/me/accounts/picnic") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"bad@email.com","password":"wrongpass"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `POST connect picnic returns 200 with zip_code when credentials are valid`() {
        given(picnicService.loginResult("user@picnic.app", "validpass"))
            .willReturn(PicnicLoginResult.Success("fake.picnic.token"))

        mvc.post("/api/v2/users/me/accounts/picnic") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@picnic.app","password":"validpass","zip_code":"10115"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.provider") { value("picnic") }
            jsonPath("$.email") { value("user@picnic.app") }
            jsonPath("$.zip_code") { value("10115") }
            jsonPath("$.connected_at") { exists() }
        }
    }

    @Test
    fun `POST connect picnic without zip_code works`() {
        given(picnicService.loginResult("user@picnic.app", "validpass"))
            .willReturn(PicnicLoginResult.Success("fake.picnic.token"))

        mvc.post("/api/v2/users/me/accounts/picnic") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@picnic.app","password":"validpass"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.zip_code") { doesNotExist() }
        }
    }

    @Test
    fun `POST connect stores encrypted values in DB`() {
        given(picnicService.loginResult("user@picnic.app", "validpass"))
            .willReturn(PicnicLoginResult.Success("fake.picnic.token"))

        mvc.post("/api/v2/users/me/accounts/picnic") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@picnic.app","password":"validpass","zip_code":"10115"}"""
        }.andExpect { status { isOk() } }

        val connection = picnicConnectionRepo.findById("dev-user-id").orElseThrow()
        assert(connection.authToken != "fake.picnic.token") { "auth_token stored in plaintext!" }
        assert(connection.encryptedPassword != null) { "encrypted_password must be stored" }
        assert(connection.encryptedPassword != "validpass") { "password stored in plaintext!" }
        assert(connection.zipCode == "10115")
        assert(connection.authToken.contains(":")) { "encrypted token must contain ':' separator" }
        assert(connection.encryptedPassword!!.contains(":")) { "encrypted password must contain ':' separator" }
    }

    // ── POST /me/accounts/picnic — 2FA required ────────────────────────────────

    @Test
    fun `POST connect returns 202 when 2FA is required`() {
        given(picnicService.loginResult("user@picnic.app", "validpass"))
            .willReturn(PicnicLoginResult.Needs2FA("partial.token"))
        given(picnicService.generateOtp("partial.token")).willReturn(true)

        mvc.post("/api/v2/users/me/accounts/picnic") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@picnic.app","password":"validpass"}"""
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.needs_2fa") { value(true) }
            jsonPath("$.email") { value("user@picnic.app") }
            jsonPath("$.message") { exists() }
        }
    }

    @Test
    fun `POST 2fa-verify stores connection after successful OTP`() {
        // Step 1: connect triggers 2FA
        given(picnicService.loginResult("user@picnic.app", "validpass"))
            .willReturn(PicnicLoginResult.Needs2FA("partial.token"))
        given(picnicService.generateOtp("partial.token")).willReturn(true)

        mvc.post("/api/v2/users/me/accounts/picnic") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@picnic.app","password":"validpass","zip_code":"10115"}"""
        }.andExpect { status { isAccepted() } }

        // Step 2: verify OTP
        given(picnicService.authenticateOtp("partial.token", "123456"))
            .willReturn("full.verified.token")

        mvc.post("/api/v2/users/me/accounts/picnic/2fa-verify") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"otp":"123456"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value("user@picnic.app") }
            jsonPath("$.zip_code") { value("10115") }
        }

        val connection = picnicConnectionRepo.findById("dev-user-id").orElseThrow()
        assert(connection.email == "user@picnic.app")
        assert(connection.zipCode == "10115")
        assert(connection.authToken.contains(":")) { "token must be encrypted" }
    }

    @Test
    fun `POST 2fa-verify returns 401 on wrong OTP`() {
        given(picnicService.loginResult("user@picnic.app", "validpass"))
            .willReturn(PicnicLoginResult.Needs2FA("partial.token"))
        given(picnicService.generateOtp("partial.token")).willReturn(true)

        mvc.post("/api/v2/users/me/accounts/picnic") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@picnic.app","password":"validpass"}"""
        }.andExpect { status { isAccepted() } }

        given(picnicService.authenticateOtp("partial.token", "000000")).willReturn(null)

        mvc.post("/api/v2/users/me/accounts/picnic/2fa-verify") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"otp":"000000"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `POST 2fa-verify returns 400 when no pending 2FA exists`() {
        mvc.post("/api/v2/users/me/accounts/picnic/2fa-verify") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"otp":"123456"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // ── GET /me after connect ──────────────────────────────────────────────────

    @Test
    fun `GET me after connect shows connected_accounts with zip_code`() {
        given(picnicService.loginResult("user@picnic.app", "validpass"))
            .willReturn(PicnicLoginResult.Success("fake.picnic.token"))

        mvc.post("/api/v2/users/me/accounts/picnic") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@picnic.app","password":"validpass","zip_code":"10115"}"""
        }

        mvc.get("/api/v2/users/me") {
            header("Authorization", auth)
        }.andExpect {
            status { isOk() }
            jsonPath("$.connected_accounts.length()") { value(1) }
            jsonPath("$.connected_accounts[0].provider") { value("picnic") }
            jsonPath("$.connected_accounts[0].email") { value("user@picnic.app") }
            jsonPath("$.connected_accounts[0].zip_code") { value("10115") }
        }
    }

    // ── DELETE /me/accounts/picnic ─────────────────────────────────────────────

    // ── PATCH /me ─────────────────────────────────────────────────────────────

    @Test
    fun `PATCH me returns 200 with updated name`() {
        mvc.patch("/api/v2/users/me") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Alice"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value("dev-user-id") }
            jsonPath("$.name") { value("Alice") }
            jsonPath("$.email") { value("dev@test.local") }
        }
    }

    @Test
    fun `PATCH me returns 401 without auth`() {
        mvc.patch("/api/v2/users/me") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Alice"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `PATCH me with null name returns current name`() {
        mvc.patch("/api/v2/users/me") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Dev User") }
        }
    }

    @Test
    fun `PATCH me preserves connected_accounts`() {
        given(picnicService.loginResult("user@picnic.app", "validpass"))
            .willReturn(PicnicLoginResult.Success("fake.picnic.token"))

        mvc.post("/api/v2/users/me/accounts/picnic") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@picnic.app","password":"validpass","zip_code":"10115"}"""
        }.andExpect { status { isOk() } }

        mvc.patch("/api/v2/users/me") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Alice"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Alice") }
            jsonPath("$.connected_accounts.length()") { value(1) }
            jsonPath("$.connected_accounts[0].email") { value("user@picnic.app") }
            jsonPath("$.connected_accounts[0].zip_code") { value("10115") }
        }
    }

    // ── DELETE /me/accounts/picnic ─────────────────────────────────────────────

    @Test
    fun `DELETE disconnect returns 204 and GET shows empty accounts`() {
        given(picnicService.loginResult("user@picnic.app", "validpass"))
            .willReturn(PicnicLoginResult.Success("fake.picnic.token"))

        mvc.post("/api/v2/users/me/accounts/picnic") {
            header("Authorization", auth)
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"user@picnic.app","password":"validpass"}"""
        }

        mvc.delete("/api/v2/users/me/accounts/picnic") {
            header("Authorization", auth)
        }.andExpect {
            status { isNoContent() }
        }

        mvc.get("/api/v2/users/me") {
            header("Authorization", auth)
        }.andExpect {
            status { isOk() }
            jsonPath("$.connected_accounts.length()") { value(0) }
        }
    }
}
