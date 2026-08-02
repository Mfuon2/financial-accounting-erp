package com.qesuite.accounting.payables.controller

import com.qesuite.accounting.payables.domain.Bill
import com.qesuite.accounting.payables.domain.BillPayment
import com.qesuite.accounting.payables.domain.BillStatus
import com.qesuite.accounting.payables.domain.PaymentRun
import com.qesuite.accounting.payables.service.ApAgeingReport
import com.qesuite.accounting.payables.service.BillService
import com.qesuite.accounting.payables.service.CreateBillRequest
import com.qesuite.accounting.payables.service.CreateDebitNoteRequest
import com.qesuite.accounting.payables.service.PaymentRunRequest
import com.qesuite.accounting.payables.service.RecordBillPaymentRequest
import com.qesuite.accounting.payables.service.UpdateBillRequest
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/bills")
@Tag(name = "Module: Accounts Payable", description = "Vendor bill management — create, approve, pay, debit notes, payment runs")
class BillController(private val billService: BillService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN','DATA_ENTRY')")
    @Operation(summary = "Create a vendor bill (DRAFT) — returns warnings on potential duplicate")
    fun createBill(@Valid @RequestBody request: CreateBillRequest): ApiResponse<Bill> {
        SecurityUtils.requireOwnEntity(request.entityId)
        val createdBy = SecurityUtils.currentUser().userId
        val result = billService.createBill(request, createdBy)
        return ApiResponse(success = true, data = result.bill, warnings = result.warnings)
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN','DATA_ENTRY')")
    @Operation(summary = "List bills for an entity (paginated)")
    fun listBills(
        @RequestParam entityId: UUID,
        @RequestParam(required = false) status: BillStatus?,
        @RequestParam(defaultValue = "0")  page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<Page<Bill>> {
        SecurityUtils.requireOwnEntity(entityId)
        return ApiResponse.success(billService.findByEntity(entityId, status, page, size))
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN','DATA_ENTRY')")
    @Operation(summary = "Get a single vendor bill by ID")
    fun getBill(@PathVariable id: UUID): ApiResponse<Bill> {
        val bill = billService.findById(id)
        SecurityUtils.requireOwnEntity(bill.entityId)
        return ApiResponse.success(bill)
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN','DATA_ENTRY')")
    @Operation(summary = "Update a DRAFT bill (items, dates, description, source document)")
    fun updateBill(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateBillRequest,
    ): ApiResponse<Bill> {
        SecurityUtils.requireOwnEntity(billService.findById(id).entityId)
        val modifiedBy = SecurityUtils.currentUser().userId
        return ApiResponse.success(billService.updateBill(id, request, modifiedBy))
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(summary = "Approve a DRAFT bill — posts AP journal entry to GL")
    fun approveBill(@PathVariable id: UUID): ApiResponse<Bill> {
        SecurityUtils.requireOwnEntity(billService.findById(id).entityId)
        return ApiResponse.success(billService.approveBill(id))
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAnyRole('SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(summary = "Void a bill")
    fun voidBill(
        @PathVariable id: UUID,
        @RequestParam(required = false) reason: String?,
    ): ApiResponse<Bill> {
        SecurityUtils.requireOwnEntity(billService.findById(id).entityId)
        return ApiResponse.success(billService.voidBill(id, reason))
    }

    @PostMapping("/{id}/debit-note")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(summary = "Raise a debit note (purchase credit note) against an approved bill — posts DR AP / CR Expense")
    fun createDebitNote(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateDebitNoteRequest,
    ): ApiResponse<Bill> {
        SecurityUtils.requireOwnEntity(billService.findById(id).entityId)
        val createdBy = SecurityUtils.currentUser().userId
        return ApiResponse.success(billService.createDebitNote(id, request, createdBy))
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(summary = "Record a single payment against a bill")
    fun recordPayment(
        @PathVariable id: UUID,
        @Valid @RequestBody request: RecordBillPaymentRequest,
    ): ApiResponse<BillPayment> {
        SecurityUtils.requireOwnEntity(billService.findById(id).entityId)
        val createdBy = SecurityUtils.currentUser().userId
        return ApiResponse.success(billService.recordPayment(id, request, createdBy))
    }

    @GetMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(summary = "List all payments for a bill")
    fun listPayments(@PathVariable id: UUID): ApiResponse<List<BillPayment>> {
        SecurityUtils.requireOwnEntity(billService.findById(id).entityId)
        return ApiResponse.success(billService.getPayments(id))
    }

    @PostMapping("/payment-run")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(summary = "Process a vendor payment run — pays multiple bills in one consolidated journal entry")
    fun processPaymentRun(@Valid @RequestBody request: PaymentRunRequest): ApiResponse<PaymentRun> {
        SecurityUtils.requireOwnEntity(request.entityId)
        val createdBy = SecurityUtils.currentUser().userId
        return ApiResponse.success(billService.processPaymentRun(request, createdBy))
    }

    @GetMapping("/payment-runs")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(summary = "List payment runs for an entity")
    fun listPaymentRuns(@RequestParam entityId: UUID): ApiResponse<List<PaymentRun>> {
        SecurityUtils.requireOwnEntity(entityId)
        return ApiResponse.success(billService.listPaymentRuns(entityId))
    }

    @GetMapping("/ageing")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(summary = "AP ageing report — outstanding bills bucketed by days overdue")
    fun getApAgeing(@RequestParam entityId: UUID): ApiResponse<ApAgeingReport> {
        SecurityUtils.requireOwnEntity(entityId)
        return ApiResponse.success(billService.getApAgeing(entityId))
    }
}
