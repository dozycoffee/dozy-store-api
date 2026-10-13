package com.dozycoffee.store.management.adapter.inbound.web.response

import com.dozycoffee.store.management.application.port.inbound.result.ManagedStoreResult

/** 내 매장 목록의 항목(docs/api/store.md `GET /me/stores`) */
data class ManagedStoreResponse(
    val storeId: Long,
    val code: String,
    val name: String,
    val status: String,
) {
    companion object {
        fun from(result: ManagedStoreResult): ManagedStoreResponse =
            ManagedStoreResponse(result.storeId.value, result.code, result.name, result.status.name)
    }
}
