package com.qesuite.accounting.shared.security

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.request.RequestPostProcessor
import java.util.UUID

/**
 * §18 — Shared MockMvc test helper. `@WithMockUser` sets the Spring Security principal to a
 * plain `org.springframework.security.core.userdetails.User`, but `SecurityUtils.currentUser()`
 * (and therefore `SecurityUtils.requireOwnEntity`) requires the principal to be a [UserContext]
 * with a real `entityId` — exactly what the production JWT filter sets. Any `@WebMvcTest`
 * exercising an endpoint that calls `SecurityUtils.currentUser()`/`requireOwnEntity()` must
 * authenticate with this helper (not `@WithMockUser`) so the request carries a matching entity.
 */
fun mockUserContext(
    entityId: UUID,
    role: UserRole = UserRole.SYSTEM_ADMIN,
    userId: UUID = UUID.randomUUID(),
    email: String = "test@example.com",
): RequestPostProcessor {
    val principal = UserContext(userId = userId, entityId = entityId, role = role, email = email)
    val auth = UsernamePasswordAuthenticationToken(
        principal, null, listOf(SimpleGrantedAuthority("ROLE_${role.name}"))
    )
    return authentication(auth)
}
