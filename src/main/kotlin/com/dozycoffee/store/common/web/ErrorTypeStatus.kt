package com.dozycoffee.store.common.web

import com.dozycoffee.store.core.ErrorType
import org.springframework.http.HttpStatus

/** `ErrorType`을 HTTP 상태로 바꾸는 유일한 곳. `else`가 없어 `ErrorType`을 추가하면 매핑 누락이 컴파일 에러로 드러난다. */
fun ErrorType.toHttpStatus(): HttpStatus =
    when (this) {
        ErrorType.INVALID_INPUT -> HttpStatus.BAD_REQUEST
        ErrorType.NOT_FOUND -> HttpStatus.NOT_FOUND
        ErrorType.CONFLICT -> HttpStatus.CONFLICT
        ErrorType.BUSINESS_RULE_VIOLATION -> HttpStatus.UNPROCESSABLE_CONTENT
    }
