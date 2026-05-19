package com.qesuite.accounting.users.repository

import com.qesuite.accounting.shared.security.UserRole
import com.qesuite.accounting.users.domain.User
import com.qesuite.accounting.users.domain.UserStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): Optional<User>
    fun findAllByEmail(email: String): List<User>
    fun findByEntityIdAndEmail(entityId: UUID, email: String): Optional<User>
    fun findByEntityId(entityId: UUID, pageable: Pageable): Page<User>
    fun findByEntityIdAndStatus(entityId: UUID, status: UserStatus, pageable: Pageable): Page<User>
    fun findByEntityIdAndRole(entityId: UUID, role: UserRole, pageable: Pageable): Page<User>
    fun existsByEntityIdAndEmail(entityId: UUID, email: String): Boolean
    fun countByEntityId(entityId: UUID): Long
    fun countByEntityIdAndStatus(entityId: UUID, status: UserStatus): Long
}
