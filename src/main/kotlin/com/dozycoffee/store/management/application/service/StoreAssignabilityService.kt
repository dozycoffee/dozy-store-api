package com.dozycoffee.store.management.application.service

import com.dozycoffee.store.common.TransactionRunner
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.application.port.inbound.CheckStoreAssignabilityUseCase
import com.dozycoffee.store.management.application.port.inbound.result.UnassignableReason
import com.dozycoffee.store.management.application.port.inbound.result.UnassignableStoreResult
import com.dozycoffee.store.management.application.port.outbound.StoreRepository
import com.dozycoffee.store.management.domain.store.StoreStatus
import org.springframework.stereotype.Service

/** Catalog가 묻는 판매 범위 대상 매장 확인(요구사항 7.2). */
@Service
class StoreAssignabilityService(
    private val stores: StoreRepository,
    private val transactions: TransactionRunner,
) : CheckStoreAssignabilityUseCase {
    override suspend fun check(storeIds: List<StoreId>): List<UnassignableStoreResult> {
        require(storeIds.size in 1..CheckStoreAssignabilityUseCase.MAX_STORE_IDS) {
            "확인할 매장은 1~${CheckStoreAssignabilityUseCase.MAX_STORE_IDS}개여야 합니다: ${storeIds.size}"
        }

        return transactions.inTransaction {
            val requested = storeIds.distinct()
            val statusById = stores.findAllByIds(requested).associate { it.id to it.status }

            requested.mapNotNull { id ->
                when (statusById[id]) {
                    null -> UnassignableStoreResult(id, UnassignableReason.NOT_FOUND)
                    StoreStatus.CLOSED -> UnassignableStoreResult(id, UnassignableReason.CLOSED)
                    StoreStatus.PREPARING, StoreStatus.OPEN, StoreStatus.SUSPENDED -> null
                }
            }
        }
    }
}
