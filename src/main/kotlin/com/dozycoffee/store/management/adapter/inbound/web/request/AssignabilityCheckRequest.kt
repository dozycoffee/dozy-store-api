package com.dozycoffee.store.management.adapter.inbound.web.request

import com.dozycoffee.store.common.web.InvalidRequestException
import com.dozycoffee.store.common.web.RequestValues
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.application.port.inbound.CheckStoreAssignabilityUseCase

/** 판매 범위 대상 매장 확인 요청(docs/api/internal.md). 값을 널 허용으로 받아 [toStoreIds]가 필드 이름과 함께 검증한다. */
data class AssignabilityCheckRequest(
    val storeIds: List<Long?>?,
) {
    /** 비어 있지 않고 [CheckStoreAssignabilityUseCase.MAX_STORE_IDS]개 이하여야 하며, 항목은 모두 양의 정수여야 한다. 개수는 중복을 빼기 전의 항목 수다. */
    fun toStoreIds(): List<StoreId> {
        val ids = RequestValues.required("storeIds", storeIds)
        if (ids.isEmpty() || ids.size > CheckStoreAssignabilityUseCase.MAX_STORE_IDS) throw InvalidRequestException("storeIds")
        return ids.map { id -> StoreId(id?.takeIf { it > 0 } ?: throw InvalidRequestException("storeIds")) }
    }
}
