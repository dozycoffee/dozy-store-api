package com.dozycoffee.store.common.paging

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("페이징")
class PagingTest {
    @Nested
    @DisplayName("PageRequest")
    inner class Request {
        @Test
        fun `기본값은 첫 페이지, 크기 20이다`() {
            val request = PageRequest()

            assertEquals(0, request.page)
            assertEquals(20, request.size)
        }

        @ParameterizedTest
        @ValueSource(ints = [1, 20, 100])
        fun `크기는 1부터 100까지 허용한다`(size: Int) {
            assertEquals(size, PageRequest(size = size).size)
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -1, 101])
        fun `크기가 범위를 벗어나면 거부한다`(size: Int) {
            assertFailsWith<IllegalArgumentException> { PageRequest(size = size) }
        }

        @Test
        fun `페이지가 음수면 거부한다`() {
            assertFailsWith<IllegalArgumentException> { PageRequest(page = -1) }
        }

        @ParameterizedTest
        @CsvSource("0, 20, 0", "1, 20, 20", "3, 20, 60", "2, 7, 14")
        fun `건너뛸 건수는 페이지와 크기의 곱이다`(
            page: Int,
            size: Int,
            offset: Long,
        ) {
            assertEquals(offset, PageRequest(page, size).offset)
        }

        @Test
        fun `건너뛸 건수가 Int 범위를 넘어도 넘치지 않는다`() {
            assertEquals(2_000_000_000L * 100, PageRequest(page = 2_000_000_000, size = 100).offset)
        }
    }

    @Nested
    @DisplayName("PageResult")
    inner class Result {
        private fun result(
            totalElements: Long,
            size: Int,
        ) = PageResult(content = emptyList<String>(), page = 0, size = size, totalElements = totalElements)

        @ParameterizedTest
        @CsvSource(
            "0, 20, 0",
            "1, 20, 1",
            "20, 20, 1",
            "21, 20, 2",
            "40, 20, 2",
            "135, 20, 7",
            "135, 100, 2",
            "5, 1, 5",
        )
        fun `전체 페이지 수는 전체 건수를 크기로 나눠 올림한 값이고 한 건도 없으면 0이다`(
            totalElements: Long,
            size: Int,
            totalPages: Int,
        ) {
            assertEquals(totalPages, result(totalElements, size).totalPages)
        }

        @Test
        fun `전체 건수가 음수면 거부한다`() {
            assertFailsWith<IllegalArgumentException> { result(-1, 20) }
        }

        @Test
        fun `map은 내용만 바꾸고 페이지 정보는 그대로 둔다`() {
            val page = PageResult(listOf(1, 2, 3), page = 2, size = 3, totalElements = 10)

            val mapped = page.map { "항목$it" }

            assertEquals(listOf("항목1", "항목2", "항목3"), mapped.content)
            assertEquals(2, mapped.page)
            assertEquals(3, mapped.size)
            assertEquals(10, mapped.totalElements)
            assertEquals(4, mapped.totalPages)
        }
    }
}
