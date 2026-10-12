package com.dozycoffee.store.common.web

/**
 * 낙관적 잠금 버전의 HTTP 표현(docs/api/README.md 낙관적 잠금). 단건 응답은 `ETag: "3"`처럼 현재 버전을 담는다.
 * `If-Match` 읽기는 버전이 필요한 요청(매장 정보 수정)을 만들 때 더한다.
 */
object VersionHeaders {
    fun etag(version: Long): String = "\"$version\""
}
