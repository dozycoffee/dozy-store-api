package com.dozycoffee.store.common.web

import com.dozycoffee.store.common.paging.PageResult
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("PageResponse, ApiPaths")
class PageResponseTest {
    @Test
    fun `페이지 결과를 응답 형식으로 바꾼다`() {
        val result = PageResult(listOf(1, 2, 3), page = 2, size = 3, totalElements = 10)

        val response = PageResponse.from(result) { "항목$it" }

        assertEquals(PageResponse(listOf("항목1", "항목2", "항목3"), page = 2, size = 3, totalElements = 10, totalPages = 4), response)
    }

    @Test
    fun `API 경로의 접두사는 api v1이다`() {
        assertEquals("/api/v1", ApiPaths.V1)
    }
}
