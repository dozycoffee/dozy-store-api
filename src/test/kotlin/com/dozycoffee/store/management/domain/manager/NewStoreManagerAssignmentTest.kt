package com.dozycoffee.store.management.domain.manager

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.fixture.principalKey
import com.dozycoffee.store.management.domain.manager.exception.InvalidManagerPrincipalTypeException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("NewStoreManagerAssignment (지정하려는 매니저)")
class NewStoreManagerAssignmentTest {
    @Test
    fun `매장과 계정을 그대로 유지한다`() {
        val principal = principalKey(PrincipalType.PARTNER)

        val assignment = NewStoreManagerAssignment(StoreId(101), principal)

        assertEquals(StoreId(101), assignment.storeId)
        assertEquals(principal, assignment.principal)
    }

    @ParameterizedTest
    @EnumSource(PrincipalType::class, names = ["PARTNER", "EMPLOYEE"])
    fun `파트너와 본사 직원 계정은 지정할 수 있다`(type: PrincipalType) {
        assertEquals(type, NewStoreManagerAssignment(StoreId(101), principalKey(type)).principal.type)
    }

    @ParameterizedTest
    @EnumSource(PrincipalType::class, names = ["PARTNER", "EMPLOYEE"], mode = EnumSource.Mode.EXCLUDE)
    fun `서비스 계정과 고객은 지정할 수 없다`(type: PrincipalType) {
        assertFailsWith<InvalidManagerPrincipalTypeException> { NewStoreManagerAssignment(StoreId(101), principalKey(type)) }
    }
}
