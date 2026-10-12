package com.dozycoffee.store.common.web

/**
 * 요청 값이 잘못됐다(필수 값 누락, 길이 초과 등). `400 INVALID_REQUEST`로 응답하고 `detail`에 문제가 된 필드 이름을 담는다.
 * domain 규칙 위반이 아니라 요청의 형식 오류라 `DomainException`이 아니다. 도메인의 `require`가 500이 되지 않도록 web이 먼저 던진다.
 *
 * @param field 문제가 된 필드나 파라미터 이름. 중첩된 필드는 `address.roadAddress`처럼 점으로 잇는다.
 */
class InvalidRequestException(
    val field: String,
) : RuntimeException("요청 값이 올바르지 않습니다: $field")
