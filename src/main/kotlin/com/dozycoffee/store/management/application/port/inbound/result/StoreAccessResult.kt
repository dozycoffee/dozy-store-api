package com.dozycoffee.store.management.application.port.inbound.result

import com.dozycoffee.store.core.StoreId

/** 접근이 허용된 매장. 폐점한 매장의 쓰기 거부처럼 상태에 따른 판단은 각 기능이 [status]를 보고 한다. */
data class StoreAccessResult(
    val storeId: StoreId,
    val status: StoreStatusResult,
)
