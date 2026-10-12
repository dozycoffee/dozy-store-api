package com.dozycoffee.store.management.application.port.inbound.result

import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.domain.store.Store

/** 요청자가 매니저인 매장 한 건(요구사항 1.3, 1.4). 매장 목록 화면에 필요한 최소 정보만 담는다. */
data class ManagedStoreResult(
    val storeId: StoreId,
    val code: String,
    val name: String,
    val status: StoreStatusResult,
) {
    companion object {
        fun from(store: Store): ManagedStoreResult =
            ManagedStoreResult(store.id, store.code.value, store.name, StoreStatusResult.valueOf(store.status.name))
    }
}
