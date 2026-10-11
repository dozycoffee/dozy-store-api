package com.dozycoffee.store.management.domain.manager

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.fixture.PARTNER_ID
import com.dozycoffee.store.fixture.principalKey
import com.dozycoffee.store.fixture.storeManagerAssignment
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

@DisplayName("StoreManagerAssignment (저장된 매니저 지정)")
class StoreManagerAssignmentTest {
    @Test
    fun `저장된 값을 그대로 유지한다`() {
        val principal = principalKey(PrincipalType.EMPLOYEE)
        val assignedAt = Instant.parse("2026-10-01T00:00:00Z")

        val assignment = storeManagerAssignment(StoreId(101), principal, assignedAt)

        assertEquals(StoreId(101), assignment.storeId)
        assertEquals(principal, assignment.principal)
        assertEquals(assignedAt, assignment.assignedAt)
    }

    @ParameterizedTest
    @EnumSource(PrincipalType::class, names = ["PARTNER", "EMPLOYEE"], mode = EnumSource.Mode.EXCLUDE)
    fun `지정할 수 없는 계정 종류가 저장되어 있으면 거부한다`(type: PrincipalType) {
        assertFailsWith<IllegalStateException> { storeManagerAssignment(principal = principalKey(type)) }
    }

    @Test
    fun `계정 ID가 같아도 계정 종류가 다르면 다른 계정이다`() {
        // 인가는 토큰의 principal key와 지정된 쌍을 종류·ID 모두 같은지 비교한다(요구사항 1.3).
        assertNotEquals(principalKey(PrincipalType.PARTNER, PARTNER_ID), principalKey(PrincipalType.EMPLOYEE, PARTNER_ID))
        assertEquals(principalKey(PrincipalType.PARTNER, PARTNER_ID), principalKey(PrincipalType.PARTNER, PARTNER_ID))
    }
}
