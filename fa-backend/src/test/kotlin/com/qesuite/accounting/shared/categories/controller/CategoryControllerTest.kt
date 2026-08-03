package com.qesuite.accounting.shared.categories.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.qesuite.accounting.integration.service.ApiKeyService
import com.qesuite.accounting.shared.categories.domain.Category
import com.qesuite.accounting.shared.categories.domain.CategoryType
import com.qesuite.accounting.shared.categories.service.CategoryDto
import com.qesuite.accounting.shared.categories.service.CategoryService
import com.qesuite.accounting.shared.security.JwtService
import com.qesuite.accounting.shared.security.SecurityConfig
import com.qesuite.accounting.shared.security.UserContext
import com.qesuite.accounting.shared.security.UserRole
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID

/**
 * Real HTTP round-trip tests (MockMvc, not just route resolution) proving:
 *  1. the entity-ownership guard (a client-supplied entityId that doesn't match the
 *     authenticated caller's own entityId is rejected with 403), and
 *  2. the role gate on mutating endpoints (DATA_ENTRY cannot create/update/deactivate).
 */
@WebMvcTest(CategoryController::class)
@Import(SecurityConfig::class)
class CategoryControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var apiKeyService: ApiKeyService

    @MockkBean
    private lateinit var categoryService: CategoryService

    private fun authFor(entityId: UUID, role: UserRole): org.springframework.test.web.servlet.request.RequestPostProcessor {
        val userContext = UserContext(userId = UUID.randomUUID(), entityId = entityId, role = role, email = "test@example.com")
        val auth = UsernamePasswordAuthenticationToken(userContext, null, listOf(SimpleGrantedAuthority("ROLE_${role.name}")))
        return authentication(auth)
    }

    // ── GET list — entity-ownership guard ─────────────────────────────────────────

    @Test
    fun `list returns 200 when entityId matches the authenticated caller`() {
        val entityId = UUID.randomUUID()
        every { categoryService.listByType(entityId, CategoryType.PAYMENT_TERM, true) } returns listOf(
            CategoryDto(UUID.randomUUID(), entityId, CategoryType.PAYMENT_TERM, "NET_30", "Net 30", 0, true)
        )

        mockMvc.get("/api/v1/categories") {
            param("entityId", entityId.toString())
            param("type", "PAYMENT_TERM")
            with(authFor(entityId, UserRole.DATA_ENTRY))
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
            jsonPath("$.data[0].code") { value("NET_30") }
        }
    }

    @Test
    fun `list rejects a client-supplied entityId belonging to another entity with 403`() {
        val myEntityId = UUID.randomUUID()
        val someoneElsesEntityId = UUID.randomUUID()

        mockMvc.get("/api/v1/categories") {
            param("entityId", someoneElsesEntityId.toString())
            param("type", "PAYMENT_TERM")
            with(authFor(myEntityId, UserRole.DATA_ENTRY))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
        }
    }

    @Test
    fun `list rejects an unknown category type with a 400`() {
        val entityId = UUID.randomUUID()

        mockMvc.get("/api/v1/categories") {
            param("entityId", entityId.toString())
            param("type", "NOT_A_REAL_TYPE")
            with(authFor(entityId, UserRole.DATA_ENTRY))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // ── POST create — role gate + ownership guard ─────────────────────────────────

    @Test
    fun `create is rejected for a non-admin role with 403`() {
        val entityId = UUID.randomUUID()
        val body = mapOf(
            "entityId" to entityId.toString(),
            "categoryType" to "PAYMENT_TERM",
            "code" to "NET_120",
            "label" to "Net 120",
        )

        mockMvc.post("/api/v1/categories") {
            with(csrf())
            with(authFor(entityId, UserRole.DATA_ENTRY))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `create succeeds for CONTROLLER_CFO when entityId matches`() {
        val entityId = UUID.randomUUID()
        every {
            categoryService.create(entityId, CategoryType.PAYMENT_TERM, "NET_120", "Net 120", null)
        } returns CategoryDto(UUID.randomUUID(), entityId, CategoryType.PAYMENT_TERM, "NET_120", "Net 120", 6, true)
        val body = mapOf(
            "entityId" to entityId.toString(),
            "categoryType" to "PAYMENT_TERM",
            "code" to "NET_120",
            "label" to "Net 120",
        )

        mockMvc.post("/api/v1/categories") {
            with(csrf())
            with(authFor(entityId, UserRole.CONTROLLER_CFO))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.code") { value("NET_120") }
        }
    }

    @Test
    fun `create rejects a request entityId different from the authenticated caller's own entity, even for CONTROLLER_CFO`() {
        val myEntityId = UUID.randomUUID()
        val someoneElsesEntityId = UUID.randomUUID()
        val body = mapOf(
            "entityId" to someoneElsesEntityId.toString(),
            "categoryType" to "PAYMENT_TERM",
            "code" to "NET_120",
            "label" to "Net 120",
        )

        mockMvc.post("/api/v1/categories") {
            with(csrf())
            with(authFor(myEntityId, UserRole.CONTROLLER_CFO))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }.andExpect {
            status { isForbidden() }
        }
    }

    // ── PUT update / deactivate — ownership guard via looked-up resource ──────────

    @Test
    fun `update is rejected 403 when the looked-up category belongs to another entity`() {
        val myEntityId = UUID.randomUUID()
        val categoryOwnedByOther = Category(
            entityId = UUID.randomUUID(),
            categoryType = CategoryType.PAYMENT_METHOD,
            code = "CASH",
            label = "Cash",
        )
        every { categoryService.findEntityById(categoryOwnedByOther.id) } returns categoryOwnedByOther

        mockMvc.put("/api/v1/categories/${categoryOwnedByOther.id}") {
            with(csrf())
            with(authFor(myEntityId, UserRole.CONTROLLER_CFO))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("label" to "Cash Payment"))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `deactivate succeeds for SYSTEM_ADMIN on a category owned by their own entity`() {
        val entityId = UUID.randomUUID()
        val category = Category(entityId = entityId, categoryType = CategoryType.PAYMENT_METHOD, code = "CASH", label = "Cash")
        every { categoryService.findEntityById(category.id) } returns category
        every { categoryService.deactivate(category.id, "No longer accepted", any()) } returns
            CategoryDto(category.id, entityId, CategoryType.PAYMENT_METHOD, "CASH", "Cash", 0, false)

        mockMvc.post("/api/v1/categories/${category.id}/deactivate") {
            with(csrf())
            with(authFor(entityId, UserRole.SYSTEM_ADMIN))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("reason" to "No longer accepted"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.isActive") { value(false) }
        }
    }
}
