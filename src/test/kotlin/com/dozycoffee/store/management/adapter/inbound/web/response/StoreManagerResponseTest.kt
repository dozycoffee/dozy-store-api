package com.dozycoffee.store.management.adapter.inbound.web.response

import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.application.port.inbound.result.StoreManagerResult
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@DisplayName("StoreManagerResponse")
class StoreManagerResponseTest {
    private val id = UUID.fromString("0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f")
    private val assignedAt = Instant.parse("2026-10-05T01:00:00Z")

    @Test
    fun `계정 종류와 ID는 Auth의 표현 그대로 소문자다`() {
        val response = StoreManagerResponse.from(StoreManagerResult(StoreId(101), PrincipalKey(PrincipalType.EMPLOYEE, id), assignedAt))

        assertEquals(101, response.storeId)
        assertEquals("employee", response.principalType)
        assertEquals("0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f", response.principalId)
        assertEquals(assignedAt, response.assignedAt)
    }

    @Test
    fun `JSON은 API 명세의 필드와 ISO-8601 UTC 시각이다`() {
        val response = StoreManagerResponse.from(StoreManagerResult(StoreId(101), PrincipalKey(PrincipalType.PARTNER, id), assignedAt))

        val node = JsonMapper().readTree(JsonMapper().writeValueAsString(response))

        assertEquals(setOf("storeId", "principalType", "principalId", "assignedAt"), node.propertyNames().toSet())
        assertEquals("partner", node.get("principalType").asString())
        assertEquals("2026-10-05T01:00:00Z", node.get("assignedAt").asString())
    }
}
