package com.dozycoffee.store.management.domain.manager

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.store.common.web.toHttpStatus
import com.dozycoffee.store.core.DomainException
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.fixture.principalKey
import com.dozycoffee.store.management.domain.manager.exception.InvalidManagerPrincipalTypeException
import com.dozycoffee.store.management.domain.manager.exception.StoreManagerAlreadyAssignedException
import com.dozycoffee.store.management.domain.manager.exception.StoreManagerErrorCode
import com.dozycoffee.store.management.domain.manager.exception.StoreManagerNotFoundException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("매장 매니저 예외")
class StoreManagerExceptionTest {
    private fun assertMapped(
        exception: DomainException,
        code: StoreManagerErrorCode,
        status: HttpStatus,
    ) {
        assertEquals(code, exception.errorCode)
        assertEquals(status, exception.errorCode.type.toHttpStatus())
    }

    @Test
    fun `예외마다 API 명세의 오류 코드와 상태 코드로 매핑된다`() {
        assertMapped(
            StoreManagerAlreadyAssignedException(StoreId(1), principalKey()),
            StoreManagerErrorCode.STORE_MANAGER_ALREADY_ASSIGNED,
            HttpStatus.CONFLICT,
        )
        assertMapped(
            StoreManagerNotFoundException(StoreId(1), principalKey()),
            StoreManagerErrorCode.STORE_MANAGER_NOT_FOUND,
            HttpStatus.NOT_FOUND,
        )
        assertMapped(
            InvalidManagerPrincipalTypeException(PrincipalType.SYSTEM),
            StoreManagerErrorCode.INVALID_MANAGER_PRINCIPAL_TYPE,
            HttpStatus.BAD_REQUEST,
        )
    }

    @Test
    fun `메시지에 원인을 알 수 있는 값을 담는다`() {
        val principal = principalKey()

        assertTrue(principal.sub in StoreManagerAlreadyAssignedException(StoreId(101), principal).message!!)
        assertTrue("101" in StoreManagerNotFoundException(StoreId(101), principal).message!!)
        assertTrue("system" in InvalidManagerPrincipalTypeException(PrincipalType.SYSTEM).message!!)
    }
}
