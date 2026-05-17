package com.qesuite.accounting.users.repository

import com.qesuite.accounting.users.domain.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, UUID> {
    fun findByTokenHashAndUsedAtIsNull(tokenHash: String): Optional<PasswordResetToken>
    fun findByUserId(userId: UUID): List<PasswordResetToken>

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now")
    fun deleteExpired(now: Instant): Int
}
