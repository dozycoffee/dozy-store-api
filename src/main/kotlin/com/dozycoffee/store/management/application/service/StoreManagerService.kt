package com.dozycoffee.store.management.application.service

import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.store.common.TransactionRunner
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.application.port.inbound.AssignStoreManagerUseCase
import com.dozycoffee.store.management.application.port.inbound.GetStoreManagersByPrincipalUseCase
import com.dozycoffee.store.management.application.port.inbound.GetStoreManagersUseCase
import com.dozycoffee.store.management.application.port.inbound.UnassignStoreManagerUseCase
import com.dozycoffee.store.management.application.port.inbound.command.AssignStoreManagerCommand
import com.dozycoffee.store.management.application.port.inbound.command.UnassignStoreManagerCommand
import com.dozycoffee.store.management.application.port.inbound.result.StoreManagerResult
import com.dozycoffee.store.management.application.port.outbound.StoreManagerAssignmentRepository
import com.dozycoffee.store.management.application.port.outbound.StoreRepository
import com.dozycoffee.store.management.domain.manager.NewStoreManagerAssignment
import com.dozycoffee.store.management.domain.manager.exception.StoreManagerNotFoundException
import com.dozycoffee.store.management.domain.store.StoreStatus
import com.dozycoffee.store.management.domain.store.exception.StoreClosedException
import com.dozycoffee.store.management.domain.store.exception.StoreNotFoundException
import org.springframework.stereotype.Service

/** 매장 매니저의 지정, 해제, 조회. 유스케이스마다 트랜잭션 경계를 연다. */
@Service
class StoreManagerService(
    private val stores: StoreRepository,
    private val managers: StoreManagerAssignmentRepository,
    private val transactions: TransactionRunner,
) : AssignStoreManagerUseCase,
    UnassignStoreManagerUseCase,
    GetStoreManagersUseCase,
    GetStoreManagersByPrincipalUseCase {
    override suspend fun assign(command: AssignStoreManagerCommand): StoreManagerResult =
        transactions.inTransaction {
            // 계정 종류 확인은 매장을 읽기 전에 한다. 요청 값 자체의 오류라 매장 상태와 무관하게 거부한다.
            val assignment = NewStoreManagerAssignment(command.storeId, command.principal)

            // 행을 잠그고 확인한다. 폐점(행을 잠그고 전이)과 겹치면 먼저 끝난 쪽이 반영된 뒤에 판단하므로, 폐점한 매장에 지정이 남지 않는다.
            val store = stores.findByIdForUpdate(command.storeId) ?: throw StoreNotFoundException(command.storeId)
            if (store.status == StoreStatus.CLOSED) throw StoreClosedException(store.id)

            StoreManagerResult.from(managers.insert(assignment))
        }

    override suspend fun unassign(command: UnassignStoreManagerCommand) {
        transactions.inTransaction {
            requireStore(command.storeId)
            if (!managers.delete(command.storeId, command.principal)) {
                throw StoreManagerNotFoundException(command.storeId, command.principal)
            }
        }
    }

    override suspend fun getByStore(storeId: StoreId): List<StoreManagerResult> =
        transactions.inTransaction {
            requireStore(storeId)
            managers.findByStoreId(storeId).map(StoreManagerResult::from)
        }

    override suspend fun getByPrincipal(principal: PrincipalKey): List<StoreManagerResult> =
        transactions.inTransaction { managers.findByPrincipal(principal).map(StoreManagerResult::from) }

    private suspend fun requireStore(storeId: StoreId) {
        stores.findById(storeId) ?: throw StoreNotFoundException(storeId)
    }
}
