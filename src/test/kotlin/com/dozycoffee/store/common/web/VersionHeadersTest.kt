package com.dozycoffee.store.common.web

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("VersionHeaders")
class VersionHeadersTest {
    @Nested
    @DisplayName("ETag")
    inner class ETag {
        @ParameterizedTest
        @CsvSource("0, '\"0\"'", "3, '\"3\"'", "9223372036854775807, '\"9223372036854775807\"'")
        fun `ETag는 버전을 따옴표로 감싼다`(
            version: Long,
            expected: String,
        ) {
            assertEquals(expected, VersionHeaders.etag(version))
        }
    }

    @Nested
    @DisplayName("If-Match 읽기")
    inner class IfMatch {
        @ParameterizedTest
        @CsvSource("'\"0\"', 0", "'\"3\"', 3", "'\"9223372036854775807\"', 9223372036854775807")
        fun `따옴표로 감싼 정수에서 버전을 읽는다`(
            header: String,
            version: Long,
        ) {
            assertEquals(version, VersionHeaders.parseIfMatch(header))
        }

        @Test
        fun `ETag로 만든 값을 그대로 읽는다`() {
            assertEquals(42L, VersionHeaders.parseIfMatch(VersionHeaders.etag(42)))
        }

        @Test
        fun `앞뒤 공백은 무시한다`() {
            assertEquals(3L, VersionHeaders.parseIfMatch("  \"3\" "))
        }

        @Test
        fun `헤더가 없으면 428을 뜻하는 예외다`() {
            assertFailsWith<VersionRequiredException> { VersionHeaders.parseIfMatch(null) }
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "3",
                "W/\"3\"",
                "*",
                "\"3\", \"4\"",
                "\"-1\"",
                "\"abc\"",
                "\"3.5\"",
                "\"03\"",
                "\"\"",
                "\"9223372036854775808\"",
                "",
                " ",
                "'3'",
                "\"3",
                "3\"",
            ],
        )
        fun `형식이 틀리면 If-Match 필드의 요청 오류다`(header: String) {
            val e = assertFailsWith<InvalidRequestException> { VersionHeaders.parseIfMatch(header) }

            assertEquals("If-Match", e.field)
        }
    }
}
