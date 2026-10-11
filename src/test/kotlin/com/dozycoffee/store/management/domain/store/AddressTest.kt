package com.dozycoffee.store.management.domain.store

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DisplayName("Address")
class AddressTest {
    private fun address(
        roadAddress: String = "서울특별시 강남구 강남대로 396",
        detailAddress: String? = "1층",
        postalCode: String = "06232",
        sido: String = "서울특별시",
        sigungu: String = "강남구",
    ) = Address(roadAddress, detailAddress, postalCode, sido, sigungu)

    @Nested
    @DisplayName("만들 때")
    inner class Create {
        @Test
        fun `값을 그대로 유지한다`() {
            val address = address()

            assertEquals("서울특별시 강남구 강남대로 396", address.roadAddress)
            assertEquals("1층", address.detailAddress)
            assertEquals("06232", address.postalCode)
            assertEquals("서울특별시", address.sido)
            assertEquals("강남구", address.sigungu)
        }

        @Test
        fun `상세 주소는 없을 수 있다`() {
            assertNull(address(detailAddress = null).detailAddress)
        }

        @Test
        fun `모든 값이 같으면 같은 주소다`() {
            assertEquals(address(), address())
        }
    }

    @Nested
    @DisplayName("필수 값 검증")
    inner class Validation {
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "\t"])
        fun `도로명 주소가 비어 있으면 거부한다`(value: String) {
            assertFailsWith<IllegalArgumentException> { address(roadAddress = value) }
        }

        @ParameterizedTest
        @ValueSource(strings = ["", " ", "\t"])
        fun `우편번호가 비어 있으면 거부한다`(value: String) {
            assertFailsWith<IllegalArgumentException> { address(postalCode = value) }
        }

        @ParameterizedTest
        @ValueSource(strings = ["", " ", "\t"])
        fun `시·도가 비어 있으면 거부한다`(value: String) {
            assertFailsWith<IllegalArgumentException> { address(sido = value) }
        }

        @ParameterizedTest
        @ValueSource(strings = ["", " ", "\t"])
        fun `시·군·구가 비어 있으면 거부한다`(value: String) {
            assertFailsWith<IllegalArgumentException> { address(sigungu = value) }
        }

        @ParameterizedTest
        @ValueSource(strings = ["", " ", "\t"])
        fun `상세 주소는 값이 있다면 공백뿐일 수 없다`(value: String) {
            assertFailsWith<IllegalArgumentException> { address(detailAddress = value) }
        }
    }
}
