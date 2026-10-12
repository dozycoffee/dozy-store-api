package com.dozycoffee.store.management.adapter.inbound.web.request

import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.application.port.inbound.command.AssignStoreManagerCommand

/** 매니저 지정 요청(docs/api/manager.md). 값을 널 허용으로 받아 [toCommand]가 필드 이름과 함께 검증한다. */
data class AssignStoreManagerRequest(
    val principalType: String?,
    val principalId: String?,
) {
    fun toCommand(storeId: StoreId): AssignStoreManagerCommand =
        AssignStoreManagerCommand(storeId, PrincipalKeyValues.of("principalType", principalType, "principalId", principalId))
}
