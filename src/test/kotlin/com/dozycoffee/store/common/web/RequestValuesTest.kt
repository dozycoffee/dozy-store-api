package com.dozycoffee.store.common.web

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DisplayName("RequestValues")
class RequestValuesTest {
    @Nested
    @DisplayName("required")
    inner class Required {
        @Test
        fun `값이 있으면 그대로 돌려준다`() {
            assertEquals(5, RequestValues.required("size", 5))
        }

        @Test
        fun `값이 없으면 필드 이름과 함께 거부한다`() {
            val e = assertFailsWith<InvalidRequestException> { RequestValues.required("address", null as String?) }

            assertEquals("address", e.field)
        }
    }

    @Nested
    @DisplayName("text")
    inner class Text {
        @Test
        fun `앞뒤 공백을 제거한 값을 돌려준다`() {
            assertEquals("강남역점", RequestValues.text("name", "  강남역점 \t", max = 50))
        }

        @Test
        fun `안쪽 공백은 그대로 둔다`() {
            assertEquals("강남 역점", RequestValues.text("name", " 강남 역점 ", max = 50))
        }

        @Test
        fun `상한 길이까지 허용한다`() {
            assertEquals(50, RequestValues.text("name", "가".repeat(50), max = 50).length)
        }

        @Test
        fun `공백을 제거한 뒤의 길이로 상한을 판단한다`() {
            assertEquals(50, RequestValues.text("name", " " + "가".repeat(50) + " ", max = 50).length)
        }

        @Test
        fun `상한을 넘으면 필드 이름과 함께 거부한다`() {
            val e = assertFailsWith<InvalidRequestException> { RequestValues.text("name", "가".repeat(51), max = 50) }

            assertEquals("name", e.field)
        }

        @Test
        fun `값이 없으면 거부한다`() {
            assertFailsWith<InvalidRequestException> { RequestValues.text("name", null, max = 50) }
        }

        @ParameterizedTest
        @ValueSource(strings = ["", " ", "\t", "\n", "  \t "])
        fun `비어 있거나 공백뿐이면 거부한다`(value: String) {
            assertFailsWith<InvalidRequestException> { RequestValues.text("name", value, max = 50) }
        }
    }

    @Nested
    @DisplayName("optionalText")
    inner class OptionalText {
        @Test
        fun `값이 없으면 null이다`() {
            assertNull(RequestValues.optionalText("detailAddress", null, max = 100))
        }

        @ParameterizedTest
        @ValueSource(strings = ["", " ", "\t", "  \n "])
        fun `비어 있거나 공백뿐이면 없는 것으로 본다`(value: String) {
            assertNull(RequestValues.optionalText("detailAddress", value, max = 100))
        }

        @Test
        fun `값이 있으면 앞뒤 공백을 제거해 돌려준다`() {
            assertEquals("1층", RequestValues.optionalText("detailAddress", " 1층 ", max = 100))
        }

        @Test
        fun `상한까지 허용하고 넘으면 거부한다`() {
            assertEquals(100, RequestValues.optionalText("detailAddress", "가".repeat(100), max = 100)?.length)
            val e = assertFailsWith<InvalidRequestException> { RequestValues.optionalText("detailAddress", "가".repeat(101), max = 100) }
            assertEquals("detailAddress", e.field)
        }
    }
}
