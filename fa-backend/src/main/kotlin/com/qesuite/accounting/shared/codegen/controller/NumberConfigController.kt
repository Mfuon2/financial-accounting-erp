package com.qesuite.accounting.shared.codegen.controller

import com.qesuite.accounting.shared.codegen.service.EntityNumberConfigService
import com.qesuite.accounting.shared.codegen.service.NumberConfigDto
import com.qesuite.accounting.shared.security.RoleSets
import com.qesuite.accounting.shared.security.SecurityUtils
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/number-config")
class NumberConfigController(
    private val service: EntityNumberConfigService,
) {

    @GetMapping
    @PreAuthorize(RoleSets.ADMIN_CONFIG_READ)
    fun getAll(@RequestParam entityId: UUID): List<NumberConfigDto> {
        SecurityUtils.requireOwnEntity(entityId)
        return service.getAll(entityId)
    }

    @PutMapping("/{moduleKey}")
    @PreAuthorize(RoleSets.ADMIN_CONFIG)
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
