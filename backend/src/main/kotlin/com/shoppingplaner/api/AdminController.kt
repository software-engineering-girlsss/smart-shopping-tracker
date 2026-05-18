package com.shoppingplaner.api

import com.shoppingplaner.dto.*
import com.shoppingplaner.security.PRINCIPAL_ATTR
import com.shoppingplaner.security.AuthPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@Tag(name = "Admin v2", description = "Admin-only endpoints — requires role: admin")
@RestController
@RequestMapping("/api/v2/admin")
class AdminController {

    // ── Users ─────────────────────────────────────────────────────────────────

    @Operation(summary = "List all registered users")
    @GetMapping("/users")
    fun listUsers(request: HttpServletRequest): ResponseEntity<Void> {
        requireAdmin(request) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
    }

    @Operation(summary = "Get a user by ID")
    @GetMapping("/users/{id}")
    fun getUser(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<Void> {
        requireAdmin(request) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
    }

    @Operation(summary = "Update a user's role or blocked status")
    @PatchMapping("/users/{id}")
    fun patchUser(
        @PathVariable id: String,
        @RequestBody req: PatchAdminUserRequest,
        request: HttpServletRequest
    ): ResponseEntity<Void> {
        requireAdmin(request) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
    }

    // ── Invite tokens ─────────────────────────────────────────────────────────

    @Operation(summary = "Generate a new invite token")
    @PostMapping("/invite-tokens")
    fun createInviteToken(request: HttpServletRequest): ResponseEntity<InviteTokenResponseDto> {
        requireAdmin(request) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val token     = UUID.randomUUID().toString()
        val expiresAt = Instant.now().epochSecond + 604_800L  // 7 days
        return ResponseEntity.status(HttpStatus.CREATED).body(InviteTokenResponseDto(token = token, expiresAt = expiresAt))
    }

    @Operation(summary = "Revoke an invite token")
    @DeleteMapping("/invite-tokens/{token}")
    fun deleteInviteToken(@PathVariable token: String, request: HttpServletRequest): ResponseEntity<Void> {
        requireAdmin(request) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        return ResponseEntity.noContent().build()
    }

    // ── Stores ────────────────────────────────────────────────────────────────

    @Operation(summary = "List store configurations")
    @GetMapping("/stores")
    fun listStores(request: HttpServletRequest): ResponseEntity<List<AdminStoreDto>> {
        requireAdmin(request) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val stores = StoresController.STORES.map { AdminStoreDto(it.id, it.name, it.available) }
        return ResponseEntity.ok(stores)
    }

    @Operation(summary = "Enable or disable a store")
    @PatchMapping("/stores/{storeId}")
    fun patchStore(
        @PathVariable storeId: String,
        @RequestBody req: PatchStoreRequest,
        request: HttpServletRequest
    ): ResponseEntity<AdminStoreDto> {
        requireAdmin(request) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val store = StoresController.STORES.firstOrNull { it.id == storeId }
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(AdminStoreDto(store.id, store.name, req.available ?: store.available))
    }

    // ── Tags ──────────────────────────────────────────────────────────────────

    @Operation(summary = "List all tags")
    @GetMapping("/tags")
    fun listTags(request: HttpServletRequest): ResponseEntity<List<AdminTagDto>> {
        requireAdmin(request) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        return ResponseEntity.ok(TagsController.TAGS.map { AdminTagDto(it.id, it.name, it.category) })
    }

    @Operation(summary = "Create a new tag")
    @PostMapping("/tags")
    fun createTag(@RequestBody req: CreateTagRequest, request: HttpServletRequest): ResponseEntity<AdminTagDto> {
        requireAdmin(request) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminTagDto(req.id, req.name, req.category))
    }

    @Operation(summary = "Delete a tag")
    @DeleteMapping("/tags/{id}")
    fun deleteTag(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<Void> {
        requireAdmin(request) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        return ResponseEntity.noContent().build()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun requireAdmin(request: HttpServletRequest): AuthPrincipal.UserAccess? {
        val principal = request.getAttribute(PRINCIPAL_ATTR) as? AuthPrincipal.UserAccess ?: return null
        return if (principal.role == "admin") principal else null
    }
}
