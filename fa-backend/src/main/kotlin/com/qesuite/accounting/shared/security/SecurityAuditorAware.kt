package com.qesuite.accounting.shared.security

import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.*

@Component
class SecurityAuditorAware : AuditorAware<UUID> {
    override fun getCurrentAuditor(): Optional<UUID> {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || !authentication.isAuthenticated || authentication.principal == "anonymousUser") {
            return Optional.empty()
        }
        
        val principal = authentication.principal
        return if (principal is UserContext) {
            Optional.of(principal.userId)
        } else {
            Optional.empty()
        }
    }
}
