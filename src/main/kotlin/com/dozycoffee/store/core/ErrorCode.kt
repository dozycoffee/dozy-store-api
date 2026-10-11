package com.dozycoffee.store.core

/** 애그리거트별 오류 코드 enum이 구현한다. [code]는 클라이언트 계약이므로 enum 이름을 바꿀 때 주의한다. */
interface ErrorCode {
    val code: String
    val type: ErrorType
}
