package com.dozycoffee.store.management.domain.store.exception

import com.dozycoffee.store.core.ErrorCode
import com.dozycoffee.store.core.ErrorType

/** 매장(`Store`)의 규칙 위반. 상태 코드는 [ErrorType]이 정한다(docs/api/README.md 도메인 오류 코드). */
enum class StoreErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    /** 매장이 없음. 매니저로 지정되지 않은 매장도 같은 코드로 응답해 매장 ID로 존재 여부를 알아낼 수 없게 한다 */
    STORE_NOT_FOUND(ErrorType.NOT_FOUND),

    /** 폐점하지 않은 다른 매장과 같은 이름 (요구사항 1.1) */
    STORE_NAME_DUPLICATED(ErrorType.CONFLICT),

    /** 허용되지 않는 상태 전환 (요구사항 1.2) */
    INVALID_STORE_STATUS_TRANSITION(ErrorType.CONFLICT),

    /** `PREPARING`이 아닌 매장의 개점 예정일 변경 (요구사항 1.2) */
    OPENING_DATE_NOT_CHANGEABLE(ErrorType.CONFLICT),

    /** 폐점한 매장의 설정 변경, 폐점한 매장에 매니저 지정 (요구사항 1.3, 1.4, 3.1) */
    STORE_CLOSED(ErrorType.CONFLICT),
    ;

    override val code: String get() = name
}
