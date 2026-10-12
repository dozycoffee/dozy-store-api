package com.dozycoffee.store.common.web

/**
 * 버전이 필요한 요청에 `If-Match` 헤더가 없다. `428 Precondition Required`(`VERSION_REQUIRED`)로 응답한다.
 * 요청 형식 오류와 같은 web 계층의 예외다. 상태 코드 하나 때문에 도메인 오류 분류(`ErrorType`)를 늘리지 않는다.
 */
class VersionRequiredException : RuntimeException("이 요청은 If-Match 헤더가 필요합니다")
