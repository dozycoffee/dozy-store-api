package com.dozycoffee.store.core

/** 도메인 오류의 분류. HTTP 상태로 바꾸는 일은 `common.web`이 한다. */
enum class ErrorType {
    /** 요청 값 자체의 형식·범위 위반 (현재 상태와 무관) */
    INVALID_INPUT,

    /** 대상이 존재하지 않음 */
    NOT_FOUND,

    /** 현재 리소스 상태와 충돌 (상태가 바뀌면 성공할 수 있음) */
    CONFLICT,

    /** 값은 유효하지만 도메인 불변식 위반 */
    BUSINESS_RULE_VIOLATION,
}
