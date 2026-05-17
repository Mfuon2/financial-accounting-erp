package com.qesuite.accounting.users.repository

import com.qesuite.accounting.users.domain.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {

    fun findByTokenHashAndRevokedAtIsNull(tokenHash: String): Optional<RefreshToken>

    fun findByUserId(userId: UUID): List<RefreshToken>

    @Modifying
    @Query(
        """
        UPDATE RefreshToken t
           SET t.revokedAt = :now,
               t.revokedBy = :revokedBy
         WHERE t.userId = :userId
           AND t.revokedAt IS NULL
        """
    )
    fun revokeAllByUser(userId: UUID, now: Instant, revokedBy: UUID): Int

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
    fun deleteExpired(now: Instant): Int
}
