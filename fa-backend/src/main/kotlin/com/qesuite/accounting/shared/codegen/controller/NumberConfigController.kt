package com.qesuite.accounting.shared.codegen.controller

import com.qesuite.accounting.shared.codegen.service.EntityNumberConfigService
import com.qesuite.accounting.shared.codegen.service.NumberConfigDto
import com.qesuite.accounting.shared.security.SecurityUtils
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/number-config")
class NumberConfigController(
    private val service: EntityNumberConfigService,
) {

    @GetMapping
    fun getAll(@RequestParam entityId: UUID): List<NumberConfigDto> {
        SecurityUtils.requireOwnEntity(entityId)
        return service.getAll(entityId)
    }

    @PutMapping("/{moduleKey}")
    fun update(
        @PathVariable moduleKey: String,
        @RequestParam entityId: UUID,
        @RequestBody body: UpdatePrefixRequest,
    ): NumberConfigDto {
        SecurityUtils.requireOwnEntity(entityId)
        return service.update(entityId, moduleKey, body.prefix, body.customFormat)
    }

    data class UpdatePrefixRequest(
        val prefix: String,
        val customFormat: String? = null,
    )
}
