package com.dozycoffee.store.common.web

import com.dozycoffee.store.common.paging.PageResult

/** 페이징 목록의 응답 형식(docs/api/README.md 페이징). */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <S, T> from(
            result: PageResult<S>,
            transform: (S) -> T,
        ): PageResponse<T> =
            PageResponse(
                content = result.content.map(transform),
                page = result.page,
                size = result.size,
                totalElements = result.totalElements,
                totalPages = result.totalPages,
            )
    }
}
