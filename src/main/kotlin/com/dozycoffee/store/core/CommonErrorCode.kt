package com.dozycoffee.store.core

/** 여러 애그리거트가 함께 쓰는 오류 코드. */
enum class CommonErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    VERSION_CONFLICT(ErrorType.CONFLICT),
    ;

    override val code: String get() = name
}
