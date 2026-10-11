package com.dozycoffee.store.management.application.port.inbound

import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.application.port.inbound.result.StoreManagerResult

interface GetStoreManagersUseCase {
    /** 매장의 매니저 목록. 지정한 순서이고, 폐점한 매장도 조회된다. 없는 매장은 `StoreNotFoundException`으로 거부한다. */
    suspend fun getByStore(storeId: StoreId): List<StoreManagerResult>
}
