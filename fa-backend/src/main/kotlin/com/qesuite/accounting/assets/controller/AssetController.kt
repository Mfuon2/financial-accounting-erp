package com.qesuite.accounting.assets.controller

import com.qesuite.accounting.assets.domain.AssetStatus
import com.qesuite.accounting.assets.domain.DepreciationMethod
import com.qesuite.accounting.assets.domain.FixedAsset
import com.qesuite.accounting.assets.service.AssetMasterService
import com.qesuite.accounting.assets.service.CreateAssetCommand
import com.qesuite.accounting.assets.service.DepreciationService
import com.qesuite.accounting.shared.dto.PagedResponse
import com.qesuite.accounting.shared.dto.toPagedResponse
import com.qesuite.accounting.shared.exceptions.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@RestController
@RequestMapping("/api/v1/assets")
@Tag(name = "Module 16: Fixed Assets", description = "Fixed asset lifecycle management — acquisition, depreciation, disposal (§16)")
class AssetController(
    private val assetMasterService: AssetMasterService,
    private val depreciationService: DepreciationService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a new fixed asset",
        description = "Registers a fixed asset with COA account mappings. Asset code must be unique within the entity."
    )
    fun createAsset(@Valid @RequestBody request: CreateAssetRequest): ApiResponse<FixedAsset> {
        val command = CreateAssetCommand(
            entityId            = request.entityId,
            periodId            = request.periodId,
            assetCode           = request.assetCode,
            assetName           = request.assetName,
            category            = request.category,
            assignedTo          = request.assignedTo,
            costAccountId       = request.costAccountId,
            accumDepAccountId   = request.accumDepAccountId,
            depExpenseAccountId = request.depExpenseAccountId,
            acquisitionDate     = request.acquisitionDate,
            acquisitionCost     = request.acquisitionCost,
            salvageValue        = request.salvageValue,
            usefulLifeMonths    = request.usefulLifeMonths,
            depreciationMethod  = request.depreciationMethod
        )
        return ApiResponse.success(assetMasterService.createAsset(command))
    }

    @GetMapping
    @Operation(
        summary = "List fixed assets",
        description = "Returns a paginated list of fixed assets for the given entity, optionally filtered by status."
    )
    fun listAssets(
        @RequestParam entityId: UUID,
        @RequestParam(required = false) status: AssetStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(defaultValue = "acquisitionDate") sort: String,
        @RequestParam(defaultValue = "ASC") direction: Sort.Direction
    ): ApiResponse<PagedResponse<FixedAsset>> {
        val pageable = PageRequest.of(page, size, Sort.by(direction, sort))
        val resultPage = if (status != null) {
            assetMasterService.findByEntityAndStatus(entityId, status, pageable)
        } else {
            assetMasterService.findByEntity(entityId, pageable)
        }
        return ApiResponse.success(resultPage.toPagedResponse { it })
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get a fixed asset by ID",
        description = "Returns the full detail of a single fixed asset."
    )
    fun getAsset(@PathVariable id: UUID): ApiResponse<FixedAsset> =
        ApiResponse.success(assetMasterService.findById(id))

    @PostMapping("/{id}/dispose")
    @Operation(
        summary = "Dispose a fixed asset",
        description = "Records the disposal of an active fixed asset. Creates and posts the disposal journal entry " +
                "(DR Cash, DR Accum Dep, CR Asset Cost, CR/DR Gain/Loss on Disposal)."
    )
    fun disposeAsset(
        @PathVariable id: UUID,
        @Valid @RequestBody request: DisposeAssetRequest
    ): ApiResponse<FixedAsset> {
        val disposed = assetMasterService.disposeAsset(
            assetId = id,
            periodId = request.periodId,
            disposalDate = request.disposalDate,
            proceedsAmount = request.proceedsAmount,
            proceedsAccountId = request.proceedsAccountId
        )
        return ApiResponse.success(disposed)
    }

    @PostMapping("/depreciation/run")
    @Operation(summary = "Run depreciation for an entity")
    fun runDepreciation(@Valid @RequestBody request: RunDepreciationRequest): ApiResponse<String> {
        depreciationService.runDepreciation(request.entityId, request.periodId, request.date)
        return ApiResponse.success("Depreciation run completed for entity ${request.entityId} on ${request.date}.")
    }

    /** Alias consumed by the frontend batch-depreciate call. */
    @PostMapping("/batch-depreciate")
    @Operation(summary = "Batch-run depreciation (alias for /depreciation/run)")
    fun batchDepreciate(@Valid @RequestBody request: RunDepreciationRequest): ApiResponse<String> {
        depreciationService.runDepreciation(request.entityId, request.periodId, request.date)
        return ApiResponse.success("Batch depreciation completed for entity ${request.entityId} on ${request.date}.")
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a fixed asset")
    fun updateAsset(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateAssetRequest
    ): ApiResponse<FixedAsset> {
        val command = com.qesuite.accounting.assets.service.UpdateAssetCommand(
            assetName          = request.assetName,
            salvageValue       = request.salvageValue,
            usefulLifeMonths   = request.usefulLifeMonths,
            depreciationMethod = request.depreciationMethod,
            category           = request.category,
            assignedTo         = request.assignedTo,
        )
        return ApiResponse.success(assetMasterService.updateAsset(id, command))
    }

    @GetMapping("/{id}/depreciation-schedule")
    @Operation(summary = "Get projected depreciation schedule for an asset")
    fun getDepreciationSchedule(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "12") months: Int
    ): ApiResponse<List<com.qesuite.accounting.assets.service.DepreciationScheduleEntry>> =
        ApiResponse.success(assetMasterService.getDepreciationSchedule(id, months))
}

// ---------------------------------------------------------------------------
// Request DTOs
// ---------------------------------------------------------------------------

data class CreateAssetRequest(
    @field:NotNull val entityId: UUID,
    val periodId: UUID? = null,
    val assetCode: String = "",
    @field:NotNull val assetName: String,
    val category: String? = null,
    val assignedTo: String? = null,
    @field:NotNull val costAccountId: UUID,
    @field:NotNull val accumDepAccountId: UUID,
    @field:NotNull val depExpenseAccountId: UUID,
    @field:NotNull val acquisitionDate: LocalDate,
    @field:NotNull @field:Positive val acquisitionCost: BigDecimal,
    val salvageValue: BigDecimal = BigDecimal.ZERO,
    @field:NotNull @field:Positive val usefulLifeMonths: Int,
    val depreciationMethod: DepreciationMethod = DepreciationMethod.STRAIGHT_LINE
)

data class DisposeAssetRequest(
    @field:NotNull val periodId: UUID,
    @field:NotNull val disposalDate: LocalDate,
    @field:NotNull @field:Positive val proceedsAmount: BigDecimal,
    @field:NotNull val proceedsAccountId: UUID
)

data class RunDepreciationRequest(
    @field:NotNull val entityId: UUID,
    @field:NotNull val periodId: UUID,
    @field:NotNull val date: LocalDate
)

data class UpdateAssetRequest(
    @field:NotNull val assetName: String,
    @field:NotNull @field:Positive val salvageValue: BigDecimal,
    @field:NotNull @field:Positive val usefulLifeMonths: Int,
    val depreciationMethod: DepreciationMethod = DepreciationMethod.STRAIGHT_LINE,
    val category: String? = null,
    val assignedTo: String? = null,
)
