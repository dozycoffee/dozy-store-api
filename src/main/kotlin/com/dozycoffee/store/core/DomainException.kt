package com.dozycoffee.store.core

/** 사용자 요청으로 생길 수 있는 도메인 규칙 위반. 규칙별로 서브클래스를 둔다. */
abstract class DomainException(
    val errorCode: ErrorCode,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
