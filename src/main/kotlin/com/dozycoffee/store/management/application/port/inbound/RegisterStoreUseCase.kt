package com.dozycoffee.store.management.application.port.inbound

import com.dozycoffee.store.management.application.port.inbound.command.RegisterStoreCommand
import com.dozycoffee.store.management.application.port.inbound.result.StoreResult

interface RegisterStoreUseCase {
    /**
     * 매장을 등록한다. 매장은 `PREPARING` 상태로 생기고 매장 코드가 부여된다(요구사항 1.1, 1.2).
     * 폐점하지 않은 다른 매장과 이름이 같으면 `StoreNameDuplicatedException`으로 거부한다.
     */
    suspend fun register(command: RegisterStoreCommand): StoreResult
}
