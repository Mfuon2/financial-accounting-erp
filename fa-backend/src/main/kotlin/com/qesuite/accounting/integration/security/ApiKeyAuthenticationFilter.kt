package com.qesuite.accounting.integration.security

import com.qesuite.accounting.integration.service.ApiKeyService
import com.qesuite.accounting.shared.security.UserContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(1)
class ApiKeyAuthenticationFilter(private val apiKeyService: ApiKeyService) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val apiKeyHeader = request.getHeader("X-Api-Key")

        if (!apiKeyHeader.isNullOrBlank() && SecurityContextHolder.getContext().authentication == null) {
            val clientIp = request.getHeader("X-Forwarded-For") ?: request.remoteAddr
            val apiKey = try {
                apiKeyService.validateApiKey(apiKeyHeader, clientIp)
            } catch (e: Exception) {
                null
            }

            if (apiKey != null) {
                val userContext = UserContext(
                    userId   = apiKey.id,
                    entityId = apiKey.entityId,
                    role     = apiKey.role,
                    email    = "api-key:${apiKey.keyId}"
                )

                val auth = UsernamePasswordAuthenticationToken(
                    userContext,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_${apiKey.role.name}"))
                )
                auth.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = auth
            }
        }

        filterChain.doFilter(request, response)
    }
}
