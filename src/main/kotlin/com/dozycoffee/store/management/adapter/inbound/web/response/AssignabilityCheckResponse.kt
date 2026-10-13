package com.dozycoffee.store.management.adapter.inbound.web.response

import com.dozycoffee.store.management.application.port.inbound.result.UnassignableStoreResult

/** 판매 범위 대상 매장 확인 응답(docs/api/internal.md). 모두 지정할 수 있으면 빈 목록이다. */
data class AssignabilityCheckResponse(
    val unassignableStores: List<UnassignableStoreResponse>,
) {
    companion object {
        fun from(results: List<UnassignableStoreResult>): AssignabilityCheckResponse =
            AssignabilityCheckResponse(results.map { UnassignableStoreResponse(it.storeId.value, it.reason.name) })
    }
}

data class UnassignableStoreResponse(
    val storeId: Long,
    val reason: String,
)
