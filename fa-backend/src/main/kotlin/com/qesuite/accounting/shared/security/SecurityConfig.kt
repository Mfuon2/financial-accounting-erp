package com.qesuite.accounting.shared.security

import com.qesuite.accounting.integration.security.ApiKeyAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtFilter: JwtAuthenticationFilter,
    private val apiKeyFilter: ApiKeyAuthenticationFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                // Actuator — health/info open for load-balancer checks; prometheus open for metrics scraping
                it.requestMatchers("/actuator/health", "/actuator/info").permitAll()
                it.requestMatchers("/actuator/prometheus").permitAll()
                // Authentication endpoints
                it.requestMatchers("/api/v1/auth/**").permitAll()
                // OpenAPI / Swagger UI (springdoc-openapi-starter-webmvc-ui)
                it.requestMatchers("/docs", "/v3/api-docs.json").permitAll()
                it.requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Webhooks are authenticated via Safaricom request signatures; no JWT required
                it.requestMatchers("/api/v1/payments/mpesa/callback").permitAll()
                // Organization sign-up — public bootstrap endpoint.
                // There is no super-admin before the first org exists, so this must be unauthenticated.
                // Only POST (create) is open; GET/PUT/suspend/activate remain protected.
                it.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/organizations").permitAll()
                it.anyRequest().authenticated()
            }
            // jwtFilter is registered just before UsernamePasswordAuthenticationFilter;
            // apiKeyFilter is then inserted before jwtFilter so it runs first.
            .addFilterBefore(jwtFilter,    UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(apiKeyFilter, jwtFilter::class.java)

        return http.build()
    }
}
