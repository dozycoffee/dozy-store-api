package com.dozycoffee.store.management.application.port.outbound

import com.dozycoffee.store.management.domain.store.StoreCode

/** 매장 코드를 발급한다. 한 번 발급한 코드는 롤백돼도 다시 나오지 않는다(요구사항 1.1). */
interface StoreCodeGenerator {
    suspend fun next(): StoreCode
}
