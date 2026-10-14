package com.dozycoffee.store.core

import com.dozycoffee.store.catalogproduct.domain.exception.CatalogProductErrorCode
import com.dozycoffee.store.common.web.toHttpStatus
import com.dozycoffee.store.management.domain.manager.exception.StoreManagerErrorCode
import com.dozycoffee.store.management.domain.store.exception.StoreErrorCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("ErrorCode")
class ErrorCodeTest {
    /** 새 `<Aggregate>ErrorCode` enum을 만들면 여기에 추가한다. */
    private val allCodes: List<ErrorCode> =
        CommonErrorCode.entries + StoreErrorCode.entries + StoreManagerErrorCode.entries + CatalogProductErrorCode.entries

    @Test
    fun `code 문자열은 전체에서 유일하다`() {
        val duplicated = allCodes.groupBy { it.code }.filterValues { it.size > 1 }.keys

        assertTrue(duplicated.isEmpty(), "중복된 code: $duplicated")
    }

    @Test
    fun `code는 대문자 스네이크 케이스다 (클라이언트 계약)`() {
        val invalid = allCodes.map { it.code }.filterNot { it.matches(Regex("[A-Z][A-Z0-9_]*")) }

        assertTrue(invalid.isEmpty(), "형식이 틀린 code: $invalid")
    }

    @Test
    fun `ErrorType은 HTTP 상태로 매핑된다`() {
        assertEquals(HttpStatus.BAD_REQUEST, ErrorType.INVALID_INPUT.toHttpStatus())
        assertEquals(HttpStatus.NOT_FOUND, ErrorType.NOT_FOUND.toHttpStatus())
        assertEquals(HttpStatus.CONFLICT, ErrorType.CONFLICT.toHttpStatus())
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, ErrorType.BUSINESS_RULE_VIOLATION.toHttpStatus())
    }
}
