package com.dozycoffee.store.management.domain.store

import com.dozycoffee.store.fixture.address
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

    @Nested
    @DisplayName("길이 상한")
    inner class Length {
        private fun text(length: Int) = "가".repeat(length)

        @Test
        fun `도로명 주소는 100자까지 허용하고 넘으면 거부한다`() {
            assertEquals(100, address(roadAddress = text(100)).roadAddress.length)
            assertFailsWith<IllegalArgumentException> { address(roadAddress = text(101)) }
        }

        @Test
        fun `상세 주소는 100자까지 허용하고 넘으면 거부한다`() {
            assertEquals(100, address(detailAddress = text(100)).detailAddress?.length)
            assertFailsWith<IllegalArgumentException> { address(detailAddress = text(101)) }
        }

        @Test
        fun `우편번호는 10자까지 허용하고 넘으면 거부한다`() {
            assertEquals(10, address(postalCode = "1".repeat(10)).postalCode.length)
            assertFailsWith<IllegalArgumentException> { address(postalCode = "1".repeat(11)) }
        }

        @Test
        fun `시·도와 시·군·구는 30자까지 허용하고 넘으면 거부한다`() {
            assertEquals(30, address(sido = text(30)).sido.length)
            assertEquals(30, address(sigungu = text(30)).sigungu.length)
            assertFailsWith<IllegalArgumentException> { address(sido = text(31)) }
            assertFailsWith<IllegalArgumentException> { address(sigungu = text(31)) }
        }

        @Test
        fun `우편번호는 형식을 검증하지 않는다`() {
            assertEquals("123-456", address(postalCode = "123-456").postalCode)
            assertEquals("ABC", address(postalCode = "ABC").postalCode)
        }

        @Test
        fun `거부 메시지에는 어느 항목이 문제인지와 길이가 담긴다`() {
            val e = assertFailsWith<IllegalArgumentException> { address(roadAddress = text(101)) }

            assertEquals("도로명 주소의 길이는 100자 이하여야 합니다: 현재 101자", e.message)
        }
    }
}
