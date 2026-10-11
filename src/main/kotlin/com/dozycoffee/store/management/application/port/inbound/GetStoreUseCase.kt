package com.dozycoffee.store.management.application.port.inbound

import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.application.port.inbound.result.StoreResult

interface GetStoreUseCase {
    /** 매장을 조회한다. 폐점한 매장도 조회된다. 없으면 `StoreNotFoundException`으로 거부한다. */
    suspend fun get(id: StoreId): StoreResult
}
