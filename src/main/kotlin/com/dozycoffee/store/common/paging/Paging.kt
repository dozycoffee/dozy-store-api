package com.dozycoffee.store.common.paging

/**
 * 목록을 나눠 받을 때의 요청(docs/api/README.md 페이징). [page]는 0부터이고 [size]는 1~[MAX_SIZE]다.
 * 범위는 web 계층이 요청 오류로 먼저 거르고, 여기서는 호출하는 코드의 실수를 한 번 더 막는다.
 */
data class PageRequest(
    val page: Int = 0,
    val size: Int = DEFAULT_SIZE,
) {
    init {
        require(page >= 0) { "페이지는 0 이상이어야 합니다: $page" }
        require(size in 1..MAX_SIZE) { "페이지 크기는 1~${MAX_SIZE}여야 합니다: $size" }
    }

    /** 건너뛸 건수 */
    val offset: Long get() = page.toLong() * size

    companion object {
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 100
    }
}

/** 한 페이지의 결과. [totalElements]는 조건에 맞는 전체 건수다. */
data class PageResult<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
) {
    init {
        require(totalElements >= 0) { "전체 건수는 0 이상이어야 합니다: $totalElements" }
    }

    /** 전체 페이지 수. 한 건도 없으면 0이다. */
    val totalPages: Int get() = ((totalElements + size - 1) / size).toInt()

    fun <R> map(transform: (T) -> R): PageResult<R> = PageResult(content.map(transform), page, size, totalElements)
}
