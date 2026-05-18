package com.shoppingplaner.api

import com.shoppingplaner.dto.*
import com.shoppingplaner.security.PRINCIPAL_ATTR
import com.shoppingplaner.security.AuthPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Tag(name = "Receipts v2", description = "Receipt scanning — parse store receipts and import to cart")
@RestController
@RequestMapping("/api/v2/receipts")
class ReceiptsController {

    @Operation(
        summary = "Scan a receipt image",
        description = "Accepts a JPEG/PNG photo of a receipt. Returns OCR-parsed items with normalized names " +
                "and matched products. Status will be `processing` initially; poll GET /api/v2/receipts/{id} for `done`."
    )
    @PostMapping("/scan", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun scan(
        @RequestParam("image") image: MultipartFile,
        request: HttpServletRequest
    ): ResponseEntity<ReceiptScanResponseDto> {
        resolveUserId(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        // Receipt OCR is a future feature — return a stub processing response
        val receiptId = "rec_${UUID.randomUUID()}"
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            ReceiptScanResponseDto(receiptId = receiptId, status = "processing")
        )
    }

    @Operation(summary = "Get receipt scan result by ID")
    @GetMapping("/{receiptId}")
    fun getReceipt(
        @PathVariable receiptId: String,
        request: HttpServletRequest
    ): ResponseEntity<ReceiptScanResponseDto> {
        resolveUserId(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.ok(ReceiptScanResponseDto(receiptId = receiptId, status = "failed"))
    }

    @Operation(
        summary = "Import receipt items into the cart",
        description = "Copies parsed receipt items into the current user's cart. " +
                "If `item_ids` is omitted all items are added."
    )
    @PostMapping("/{receiptId}/to-cart")
    fun toCart(
        @PathVariable receiptId: String,
        @RequestBody(required = false) req: AddReceiptToCartRequest?,
        request: HttpServletRequest
    ): ResponseEntity<Void> {
        resolveUserId(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
    }

    @Operation(summary = "Show potential savings if the receipt items had been bought elsewhere")
    @GetMapping("/{receiptId}/savings")
    fun savings(
        @PathVariable receiptId: String,
        request: HttpServletRequest
    ): ResponseEntity<ReceiptSavingsDto> {
        resolveUserId(request) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.ok(
            ReceiptSavingsDto(totalPaid = 0.0, storeDetected = null, alternatives = emptyList())
        )
    }

    private fun resolveUserId(request: HttpServletRequest): String? =
        (request.getAttribute(PRINCIPAL_ATTR) as? AuthPrincipal.UserAccess)?.userId
}
