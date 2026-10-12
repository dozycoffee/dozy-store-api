package com.dozycoffee.store.management.application.port.inbound

import com.dozycoffee.store.management.application.port.inbound.command.AssignStoreManagerCommand
import com.dozycoffee.store.management.application.port.inbound.result.StoreManagerResult

interface AssignStoreManagerUseCase {
    /**
     * 계정을 매장의 매니저로 지정한다(요구사항 1.3). Store는 계정이 실제로 있는지 확인하지 않는다.
     * 매니저로 지정할 수 없는 계정 종류는 `InvalidManagerPrincipalTypeException`, 없는 매장은 `StoreNotFoundException`,
     * 폐점한 매장은 `StoreClosedException`, 이미 지정된 계정은 `StoreManagerAlreadyAssignedException`으로 거부한다.
     */
    suspend fun assign(command: AssignStoreManagerCommand): StoreManagerResult
}
