package com.dozycoffee.store.management.application.port.inbound.command

import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.store.core.StoreId

/** 매장 매니저 지정 요청. 계정은 Auth의 principal key(계정 종류와 계정 ID의 쌍)로 가리킨다(요구사항 1.3). */
data class AssignStoreManagerCommand(
    val storeId: StoreId,
    val principal: PrincipalKey,
)
