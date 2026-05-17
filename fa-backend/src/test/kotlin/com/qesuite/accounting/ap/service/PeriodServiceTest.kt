package com.qesuite.accounting.ap.service

import com.qesuite.accounting.ap.domain.Period
import com.qesuite.accounting.ap.domain.PeriodStatus
import com.qesuite.accounting.ap.repository.PeriodRepository
import com.qesuite.accounting.shared.exceptions.ValidationException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.*

class PeriodServiceTest {

    private val periodRepository = mockk<PeriodRepository>()
    private val periodService = PeriodService(periodRepository)

    private val entityId = UUID.randomUUID()

    @Test
    fun `should generate 12 monthly periods`() {
        // Given — stub the duplicate-year guard added for idempotency (Fix #9)
        every { periodRepository.existsByEntityIdAndPeriodName(entityId, any()) } returns false
        every { periodRepository.saveAll<Period>(any()) } returns emptyList()

        // When
        periodService.generateFiscalYear(entityId, 2024)

        // Then
        verify { periodRepository.saveAll<Period>(withArg {
            val list = it.toList()
            assertEquals(12, list.size)
            assertEquals(LocalDate.of(2024, 1, 1), list[0].startDate)
            assertEquals(LocalDate.of(2024, 1, 31), list[0].endDate)
            assertEquals(PeriodStatus.OPEN, list[0].status)
            assertEquals(PeriodStatus.FUTURE, list[1].status)
            assertEquals(LocalDate.of(2024, 12, 1), list[11].startDate)
            assertEquals(LocalDate.of(2024, 12, 31), list[11].endDate)
        }) }
    }

    @Test
    fun `should find period for date`() {
        // Given
        val date = LocalDate.of(2024, 5, 15)
        val period = mockk<Period>()
        every { periodRepository.findByEntityIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(entityId, date, date) } returns Optional.of(period)

        // When
        val result = periodService.findPeriodForDate(entityId, date)

        // Then
        assertEquals(period, result)
    }

    @Test
    fun `should throw error when period not found for date`() {
        // Given
        val date = LocalDate.of(2024, 5, 15)
        every { periodRepository.findByEntityIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(entityId, date, date) } returns Optional.empty()

        // When/Then
        assertThrows<ValidationException> {
            periodService.findPeriodForDate(entityId, date)
        }
    }

    @Test
    fun `should successfully transition period status`() {
        // Given
        val periodId = UUID.randomUUID()
        val period = Period(entityId, "JAN 2024", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), PeriodStatus.OPEN)
        every { periodRepository.findById(periodId) } returns Optional.of(period)
        every { periodRepository.save(any()) } answers { it.invocation.args[0] as Period }

        // When
        periodService.transitionPeriod(periodId, PeriodStatus.ADJUSTING)

        // Then
        assertEquals(PeriodStatus.ADJUSTING, period.status)
        verify { periodRepository.save(period) }
    }

    @Test
    fun `should throw error on invalid transition`() {
        // Given
        val periodId = UUID.randomUUID()
        val period = Period(entityId, "JAN 2024", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), PeriodStatus.CLOSED)
        every { periodRepository.findById(periodId) } returns Optional.of(period)

        // When/Then
        assertThrows<ValidationException> {
            periodService.transitionPeriod(periodId, PeriodStatus.OPEN)
        }
    }
}
