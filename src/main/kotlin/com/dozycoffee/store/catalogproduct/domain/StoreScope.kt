package com.dozycoffee.store.catalogproduct.domain

import com.dozycoffee.store.core.StoreId

/**
 * 상품의 판매 범위(요구사항 7.1). 모든 매장이거나 대상 매장 목록이다.
 *
 * `All`에는 대상 매장이 없으므로 "`All`인데 대상 매장이 있는" 모순 상태를 만들 수 없다.
 * 대상이 빈 [Limited]는 어떤 매장도 포함하지 않는다.
 */
sealed interface StoreScope {
    /** 이 매장이 판매 범위에 포함되는가(노출 판단 2단계, 요구사항 4장). */
    fun covers(storeId: StoreId): Boolean

    /** 모든 매장 */
    data object All : StoreScope {
        override fun covers(storeId: StoreId): Boolean = true
    }

    /** 대상 매장만. 같은 매장이 여러 번 와도 하나로 본다. */
    data class Limited(
        val targetStoreIds: Set<StoreId>,
    ) : StoreScope {
        override fun covers(storeId: StoreId): Boolean = storeId in targetStoreIds
    }
}
