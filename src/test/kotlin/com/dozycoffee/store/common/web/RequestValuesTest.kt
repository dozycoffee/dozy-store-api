package com.dozycoffee.store.common.web

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private enum class Color { RED, GREEN }

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

    @Nested
    @DisplayName("positiveLong, positiveLongs")
    inner class Ids {
        @Test
        fun `양의 정수를 읽는다`() {
            assertEquals(101L, RequestValues.positiveLong("storeId", "101"))
            assertEquals(101L, RequestValues.positiveLong("storeId", " 101 "))
        }

        @ParameterizedTest
        @ValueSource(strings = ["0", "-1", "abc", "1.5", "", " ", "9223372036854775808"])
        fun `양의 정수가 아니면 필드 이름과 함께 거부한다`(value: String) {
            val e = assertFailsWith<InvalidRequestException> { RequestValues.positiveLong("storeId", value) }

            assertEquals("storeId", e.field)
        }

        @Test
        fun `값이 없으면 거부한다`() {
            assertFailsWith<InvalidRequestException> { RequestValues.positiveLong("storeId", null) }
        }

        @Test
        fun `쉼표로 구분한 목록을 읽고 같은 값은 한 번만 담는다`() {
            assertEquals(setOf(1L, 2L, 3L), RequestValues.positiveLongs("ids", "1, 2,3,2", max = 100))
        }

        @Test
        fun `값이 없거나 비어 있으면 빈 집합이다`() {
            assertEquals(emptySet(), RequestValues.positiveLongs("ids", null, max = 100))
            assertEquals(emptySet(), RequestValues.positiveLongs("ids", "", max = 100))
            assertEquals(emptySet(), RequestValues.positiveLongs("ids", "  ", max = 100))
        }

        @ParameterizedTest
        @ValueSource(strings = ["1,abc", "1,,2", "1,", ",1", "0,1", "-3"])
        fun `항목이 비었거나 양의 정수가 아니면 거부한다`(value: String) {
            assertFailsWith<InvalidRequestException> { RequestValues.positiveLongs("ids", value, max = 100) }
        }

        @Test
        fun `상한 개수까지 허용하고 넘으면 거부한다`() {
            assertEquals(100, RequestValues.positiveLongs("ids", (1..100).joinToString(","), max = 100).size)
            assertFailsWith<InvalidRequestException> { RequestValues.positiveLongs("ids", (1..101).joinToString(","), max = 100) }
        }
    }

    @Nested
    @DisplayName("enums, int")
    inner class EnumsAndInts {
        @Test
        fun `쉼표로 구분한 enum 이름을 읽는다`() {
            assertEquals(setOf(Color.RED, Color.GREEN), RequestValues.enums("status", "RED, GREEN,RED", Color::class.java))
        }

        @Test
        fun `값이 없거나 비어 있으면 빈 집합이다`() {
            assertEquals(emptySet(), RequestValues.enums("status", null, Color::class.java))
            assertEquals(emptySet(), RequestValues.enums("status", " ", Color::class.java))
        }

        @ParameterizedTest
        @ValueSource(strings = ["BLUE", "red", "RED,", "RED,,GREEN", "RED,BLUE"])
        fun `허용되지 않는 이름이거나 항목이 비었으면 거부한다 (대소문자를 구분한다)`(value: String) {
            val e = assertFailsWith<InvalidRequestException> { RequestValues.enums("status", value, Color::class.java) }

            assertEquals("status", e.field)
        }

        @Test
        fun `정수는 없으면 기본값이고 범위 안이면 읽는다`() {
            assertEquals(20, RequestValues.int("size", null, default = 20, range = 1..100))
            assertEquals(1, RequestValues.int("size", "1", default = 20, range = 1..100))
            assertEquals(100, RequestValues.int("size", " 100 ", default = 20, range = 1..100))
        }

        @ParameterizedTest
        @ValueSource(strings = ["0", "101", "-1", "abc", "", "1.5", "99999999999"])
        fun `정수가 아니거나 범위를 벗어나면 거부한다`(value: String) {
            val e = assertFailsWith<InvalidRequestException> { RequestValues.int("size", value, default = 20, range = 1..100) }

            assertEquals("size", e.field)
        }
    }
}
