package com.dozycoffee.store.common.web

/**
 * 낙관적 잠금 버전의 HTTP 표현(docs/api/README.md 낙관적 잠금).
 * 단건 응답은 `ETag: "3"`처럼 현재 버전을 담고, 버전이 필요한 요청은 그 값을 `If-Match: "3"`으로 돌려보낸다.
 */
object VersionHeaders {
    /** 따옴표로 감싼 정수 하나만 받는다. 앞자리 0은 허용하지 않는다(우리가 만드는 `ETag`가 아니다). */
    private val IF_MATCH_PATTERN = Regex("\"(0|[1-9][0-9]*)\"")

    const val IF_MATCH_FIELD = "If-Match"

    fun etag(version: Long): String = "\"$version\""

    /**
     * `If-Match` 헤더에서 버전을 읽는다. 헤더가 없으면 [VersionRequiredException](428)이다.
     * 따옴표 없는 값, 약한 검증자(`W/"3"`), `*`, 여러 값, 음수, 숫자가 아니거나 `Long` 범위를 넘는 값은
     * [InvalidRequestException](400, 필드 `If-Match`)이다. 비어 있는 헤더도 형식 오류다.
     */
    fun parseIfMatch(header: String?): Long {
        if (header == null) throw VersionRequiredException()
        val digits = IF_MATCH_PATTERN.matchEntire(header.trim())?.groupValues?.get(1)
        return digits?.toLongOrNull() ?: throw InvalidRequestException(IF_MATCH_FIELD)
    }
}
