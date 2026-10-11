package com.dozycoffee.store.core

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("ID 타입")
class IdsTest {
    @Nested
    @DisplayName("StoreId, ProductId")
    inner class NumericIds {
        @Test
        fun `양수만 만들 수 있다`() {
            assertEquals(1L, StoreId(1).value)
            assertEquals(1L, ProductId(1).value)
        }

        @Test
        fun `0과 음수는 거부한다`() {
            assertFailsWith<IllegalArgumentException> { StoreId(0) }
            assertFailsWith<IllegalArgumentException> { ProductId(-1) }
        }

        @Test
        fun `상품 ID는 값 순서로 비교한다 (본사 등록 순)`() {
            assertTrue(ProductId(3) < ProductId(10))
        }
    }

    @Nested
    @DisplayName("Sku")
    inner class SkuTest {
        @Test
        fun `공백뿐인 값은 거부한다`() {
            assertFailsWith<IllegalArgumentException> { Sku(" ") }
        }

        @Test
        fun `값을 그대로 유지한다`() {
            assertEquals("DZ-00000123", Sku("DZ-00000123").value)
        }
    }
}
