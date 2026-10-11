package com.dozycoffee.store.management.application.port.inbound

import com.dozycoffee.store.management.application.port.inbound.command.UnassignStoreManagerCommand

interface UnassignStoreManagerUseCase {
    /**
     * 매니저 지정을 해제한다. 매장의 개별 설정은 바뀌지 않는다. 폐점한 매장에서도 해제할 수 있다.
     * 없는 매장은 `StoreNotFoundException`, 지정이 없으면 `StoreManagerNotFoundException`으로 거부한다.
     */
    suspend fun unassign(command: UnassignStoreManagerCommand)
}
