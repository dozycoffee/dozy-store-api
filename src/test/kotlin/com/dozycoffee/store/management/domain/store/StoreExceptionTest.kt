package com.dozycoffee.store.management.domain.store

import com.dozycoffee.store.common.web.toHttpStatus
import com.dozycoffee.store.core.DomainException
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.domain.store.exception.InvalidStoreStatusTransitionException
import com.dozycoffee.store.management.domain.store.exception.OpeningDateNotChangeableException
import com.dozycoffee.store.management.domain.store.exception.StoreClosedException
import com.dozycoffee.store.management.domain.store.exception.StoreErrorCode
import com.dozycoffee.store.management.domain.store.exception.StoreNameDuplicatedException
import com.dozycoffee.store.management.domain.store.exception.StoreNotFoundException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("Store 예외")
class StoreExceptionTest {
    private fun assertMapped(
        exception: DomainException,
        code: StoreErrorCode,
        status: HttpStatus,
    ) {
        assertEquals(code, exception.errorCode)
        assertEquals(status, exception.errorCode.type.toHttpStatus())
    }

    @Test
    fun `예외마다 API 명세의 오류 코드와 상태 코드로 매핑된다`() {
        assertMapped(StoreNotFoundException(StoreId(1)), StoreErrorCode.STORE_NOT_FOUND, HttpStatus.NOT_FOUND)
        assertMapped(StoreNameDuplicatedException("강남역점"), StoreErrorCode.STORE_NAME_DUPLICATED, HttpStatus.CONFLICT)
        assertMapped(
            InvalidStoreStatusTransitionException(StoreStatus.OPEN, StoreStatus.OPEN),
            StoreErrorCode.INVALID_STORE_STATUS_TRANSITION,
            HttpStatus.CONFLICT,
        )
        assertMapped(
            OpeningDateNotChangeableException(StoreStatus.OPEN),
            StoreErrorCode.OPENING_DATE_NOT_CHANGEABLE,
            HttpStatus.CONFLICT,
        )
        assertMapped(StoreClosedException(StoreId(1)), StoreErrorCode.STORE_CLOSED, HttpStatus.CONFLICT)
    }

    @Test
    fun `메시지에 원인을 알 수 있는 값을 담는다`() {
        assertTrue("101" in StoreNotFoundException(StoreId(101)).message!!)
        assertTrue("강남역점" in StoreNameDuplicatedException("강남역점").message!!)
        val transition = InvalidStoreStatusTransitionException(StoreStatus.CLOSED, StoreStatus.OPEN).message!!
        assertTrue("CLOSED" in transition && "OPEN" in transition)
        assertTrue("SUSPENDED" in OpeningDateNotChangeableException(StoreStatus.SUSPENDED).message!!)
        assertTrue("101" in StoreClosedException(StoreId(101)).message!!)
    }
}
