package com.dozycoffee.store.management.domain.store

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("StoreCode")
class StoreCodeTest {
    @Nested
    @DisplayName("일련번호로 새로 만들 때")
    inner class Create {
        @Test
        fun `접두사와 8자리 일련번호로 만든다`() {
            assertEquals("ST-00000123", StoreCode.create(123).value)
        }

        @Test
        fun `8자리보다 짧으면 앞을 0으로 채운다`() {
            assertEquals("ST-00000001", StoreCode.create(1).value)
        }

        @Test
        fun `8자리를 꽉 채우는 최대 일련번호까지 만들 수 있다`() {
            assertEquals("ST-99999999", StoreCode.create(99_999_999).value)
        }

        @ParameterizedTest
        @ValueSource(longs = [0, -1, 100_000_000])
        fun `일련번호가 1 미만이거나 8자리를 넘으면 거부한다`(sequence: Long) {
            assertFailsWith<IllegalArgumentException> { StoreCode.create(sequence) }
        }
    }

    @Nested
    @DisplayName("문자열로 만들 때 (DB 복원, 외부에서 받은 값)")
    inner class FromString {
        @Test
        fun `올바른 형식이면 값을 그대로 유지한다`() {
            assertEquals("ST-00000123", StoreCode("ST-00000123").value)
        }

        @Test
        fun `create로 만든 값과 같은 값이면 같다`() {
            assertEquals(StoreCode.create(123), StoreCode("ST-00000123"))
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "",
                "00000123",
                "ST00000123",
                "st-00000123",
                "DZ-00000123",
                "ST-0000123",
                "ST-000001234",
                "ST-0000012a",
                "ST-0000 123",
                " ST-00000123",
                "ST-00000123 ",
                "ST-00000123\n",
            ],
        )
        fun `형식이 올바르지 않으면 거부한다`(value: String) {
            assertFailsWith<IllegalArgumentException> { StoreCode(value) }
        }
    }

    @Test
    fun `문자열로는 값 그대로 표현한다`() {
        assertEquals("ST-00000123", StoreCode.create(123).toString())
    }
}
