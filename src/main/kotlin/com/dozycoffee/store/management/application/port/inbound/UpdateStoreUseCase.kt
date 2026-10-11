package com.dozycoffee.store.management.application.port.inbound

import com.dozycoffee.store.management.application.port.inbound.command.UpdateStoreCommand
import com.dozycoffee.store.management.application.port.inbound.result.StoreResult

interface UpdateStoreUseCase {
    /**
     * 매장 정보를 수정한다(요구사항 1.1, 1.2).
     * 없는 매장은 `StoreNotFoundException`, 그 사이 다른 변경이 반영됐으면 `VersionConflictException`,
     * 폐점하지 않은 다른 매장과 이름이 같으면 `StoreNameDuplicatedException`,
     * 개점 준비가 아닌 매장의 개점 예정일 변경은 `OpeningDateNotChangeableException`으로 거부한다.
     * 폐점한 매장도 수정할 수 있다.
     */
    suspend fun update(command: UpdateStoreCommand): StoreResult
}
