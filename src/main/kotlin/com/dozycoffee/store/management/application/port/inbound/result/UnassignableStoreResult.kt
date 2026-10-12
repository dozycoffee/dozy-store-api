package com.dozycoffee.store.management.application.port.inbound.result

import com.dozycoffee.store.core.StoreId

/** 판매 범위의 대상으로 지정할 수 없는 매장과 그 이유(요구사항 7.2). */
data class UnassignableStoreResult(
    val storeId: StoreId,
    val reason: UnassignableReason,
)

enum class UnassignableReason {
    /** 없는 매장 */
    NOT_FOUND,

    /** 폐점한 매장. 폐점한 매장은 다시 열 수 없으므로 판매 범위에 새로 넣지 않는다(요구사항 1.4). */
    CLOSED,
}
