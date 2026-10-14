package com.dozycoffee.store.catalogproduct.domain

import com.dozycoffee.store.catalogproduct.domain.exception.CatalogProductErrorCode
import com.dozycoffee.store.catalogproduct.domain.exception.ProductNotFoundException
import com.dozycoffee.store.common.web.toHttpStatus
import com.dozycoffee.store.core.ProductId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("CatalogProduct 예외")
class CatalogProductExceptionTest {
    @Test
    fun `상품을 찾을 수 없는 예외는 API 명세의 오류 코드와 404로 매핑된다`() {
        val exception = ProductNotFoundException(ProductId(1))

        assertEquals(CatalogProductErrorCode.PRODUCT_NOT_FOUND, exception.errorCode)
        assertEquals("PRODUCT_NOT_FOUND", exception.errorCode.code)
        assertEquals(HttpStatus.NOT_FOUND, exception.errorCode.type.toHttpStatus())
    }

    @Test
    fun `메시지에 상품 ID를 담는다`() {
        assertTrue("1024" in ProductNotFoundException(ProductId(1024)).message!!)
    }
}
