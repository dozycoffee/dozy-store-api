package com.dozycoffee.store.management.application.port.inbound.command

import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.store.core.StoreId

/** 매장 매니저 지정 해제 요청. */
data class UnassignStoreManagerCommand(
    val storeId: StoreId,
    val principal: PrincipalKey,
)
