package com.dozycoffee.store.management.application.service

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.store.common.TransactionRunner
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.application.port.inbound.CheckStoreAccessUseCase
import com.dozycoffee.store.management.application.port.inbound.Requester
import com.dozycoffee.store.management.application.port.inbound.StoreAccessMode
import com.dozycoffee.store.management.application.port.inbound.StoreRoles
import com.dozycoffee.store.management.application.port.inbound.result.StoreAccessResult
import com.dozycoffee.store.management.application.port.inbound.result.StoreStatusResult
import com.dozycoffee.store.management.application.port.outbound.StoreManagerAssignmentRepository
import com.dozycoffee.store.management.application.port.outbound.StoreRepository
import com.dozycoffee.store.management.domain.store.exception.StoreNotFoundException
import org.springframework.stereotype.Service

/** 요청자가 매장을 다룰 수 있는지 판단한다(요구사항 2장). 매장별 판단만 하고, 역할별 경로 인가는 web 계층이 한다. */
@Service
class StoreAccessService(
    private val stores: StoreRepository,
    private val managers: StoreManagerAssignmentRepository,
    private val transactions: TransactionRunner,
) : CheckStoreAccessUseCase {
    override suspend fun check(
        requester: Requester,
        storeId: StoreId,
        mode: StoreAccessMode,
    ): StoreAccessResult =
        transactions.inTransaction {
            val store = stores.findById(storeId) ?: throw StoreNotFoundException(storeId)
            if (!canAccess(requester, store.id, mode)) throw StoreNotFoundException(storeId)
            StoreAccessResult(store.id, StoreStatusResult.valueOf(store.status.name))
        }

    private suspend fun canAccess(
        requester: Requester,
        storeId: StoreId,
        mode: StoreAccessMode,
    ): Boolean =
        when {
            requester.hasRole(StoreRoles.ADMIN) -> true
            requester.hasRole(StoreRoles.VIEWER) && mode == StoreAccessMode.READ -> true
            // 조회 권한만으로는 쓸 수 없다. 매니저로도 지정되어 있다면 아래에서 인정한다.
            else -> isManagerOf(requester, storeId)
        }

    /** 매니저로 인정되는 계정(파트너, 또는 `store:manager` 역할을 가진 직원)이고, 그 매장에 지정되어 있는가. */
    private suspend fun isManagerOf(
        requester: Requester,
        storeId: StoreId,
    ): Boolean {
        val eligible =
            when (requester.principal.type) {
                PrincipalType.PARTNER -> true
                PrincipalType.EMPLOYEE -> requester.hasRole(StoreRoles.MANAGER)
                PrincipalType.SYSTEM, PrincipalType.CUSTOMER -> false
            }
        return eligible && managers.exists(storeId, requester.principal)
    }
}
