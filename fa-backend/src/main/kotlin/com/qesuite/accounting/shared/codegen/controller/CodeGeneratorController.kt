package com.qesuite.accounting.shared.codegen.controller

import com.qesuite.accounting.shared.codegen.service.CodeGeneratorService
import com.qesuite.accounting.shared.codegen.service.EntityNumberConfigService
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.RoleSets
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/codes")
@Tag(
    name = "Code Generator",
    description = """
Generates sequential, entity-scoped business codes for any record type.

`GET /next` — peek (read-only UI hint, no sequence consumed). Pass `moduleKey` (e.g. FIXED_ASSET, CUSTOMER) to honour the org's configured prefix and format.
`GET /generate` — consume next sequence (call once at record creation).
"""
)
class CodeGeneratorController(
    private val codeGeneratorService: CodeGeneratorService,
    private val numberConfigService: EntityNumberConfigService,
) {

    @GetMapping("/next")
    @Operation(
        summary = "Peek at the next code (read-only)",
        description = "Returns what the next code would be **without** incrementing the sequence. " +
                "Pass `moduleKey` to apply the org's configured prefix and custom format."
    )
    @PreAuthorize(RoleSets.BROAD_READ)
    fun peek(
        @RequestParam @Parameter(description = "Entity UUID", required = true) entityId: UUID,
        @RequestParam @Parameter(description = "Code prefix, e.g. CU, SUPP, INV", required = true) prefix: String,
        @RequestParam @Parameter(description = "Module key, e.g. FIXED_ASSET, CUSTOMER", required = false)
        moduleKey: String? = null,
    ): ApiResponse<CodeSuggestion> {
        SecurityUtils.requireOwnEntity(entityId)
        val code = if (moduleKey != null) {
            val cfg = numberConfigService.resolveConfig(entityId, moduleKey)
            codeGeneratorService.peek(entityId, cfg.prefix, cfg.yearScoped, customFormat = cfg.customFormat)
        } else {
            codeGeneratorService.peek(entityId, prefix)
        }
        return ApiResponse.success(CodeSuggestion(prefix.uppercase(), code))
    }

    @GetMapping("/generate")
    @Operation(
        summary = "Consume and return the next code",
        description = "Atomically increments the sequence and returns the next code. Call this once per record creation if you need a pre-committed code. Normally, each create endpoint calls the service internally."
    )
    @PreAuthorize(RoleSets.BROAD_READ)
    fun generate(
        @RequestParam @Parameter(description = "Entity UUID", required = true) entityId: UUID,
        @RequestParam @Parameter(description = "Code prefix, e.g. CU, SUPP, INV", required = true) prefix: String
    ): ApiResponse<CodeSuggestion> {
        SecurityUtils.requireOwnEntity(entityId)
        return ApiResponse.success(CodeSuggestion(prefix.uppercase(), codeGeneratorService.next(entityId, prefix)))
    }

    @GetMapping("/all")
    @Operation(
        summary = "Peek at the next code for all known prefixes",
        description = "Convenience endpoint — returns a map of prefix → next code for every registered prefix. Useful for dashboard or bulk form pre-population."
    )
    @PreAuthorize(RoleSets.BROAD_READ)
    fun peekAll(
        @RequestParam @Parameter(description = "Entity UUID", required = true) entityId: UUID
    ): ApiResponse<Map<String, String>> {
        SecurityUtils.requireOwnEntity(entityId)
        return ApiResponse.success(codeGeneratorService.peekAll(entityId))
    }
}

data class CodeSuggestion(val prefix: String, val code: String)
