package com.dozycoffee.store.management.domain.manager.exception

import com.dozycoffee.store.core.ErrorCode
import com.dozycoffee.store.core.ErrorType

/** 매장 매니저 지정의 규칙 위반. 상태 코드는 [ErrorType]이 정한다(docs/api/README.md 도메인 오류 코드). */
enum class StoreManagerErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    /** 이미 그 매장의 매니저인 계정을 다시 지정 (요구사항 1.3) */
    STORE_MANAGER_ALREADY_ASSIGNED(ErrorType.CONFLICT),

    /** 해제하려는 매니저 지정이 없음 */
    STORE_MANAGER_NOT_FOUND(ErrorType.NOT_FOUND),

    /** 매니저로 지정할 수 없는 계정 종류(`system`, `customer`) (요구사항 1.3) */
    INVALID_MANAGER_PRINCIPAL_TYPE(ErrorType.INVALID_INPUT),
    ;

    override val code: String get() = name
}
