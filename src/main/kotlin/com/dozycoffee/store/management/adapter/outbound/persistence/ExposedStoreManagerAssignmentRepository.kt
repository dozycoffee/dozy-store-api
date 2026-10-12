package com.dozycoffee.store.management.adapter.outbound.persistence

import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.store.common.exposed.isUniqueViolationOf
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.application.port.outbound.StoreManagerAssignmentRepository
import com.dozycoffee.store.management.domain.manager.NewStoreManagerAssignment
import com.dozycoffee.store.management.domain.manager.StoreManagerAssignment
import com.dozycoffee.store.management.domain.manager.exception.StoreManagerAlreadyAssignedException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insertReturning
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.springframework.stereotype.Repository

@Repository
class ExposedStoreManagerAssignmentRepository : StoreManagerAssignmentRepository {
    override suspend fun insert(assignment: NewStoreManagerAssignment): StoreManagerAssignment =
        try {
            StoreManagersTable
                .insertReturning {
                    it[storeId] = assignment.storeId.value
                    it[principalType] = assignment.principal.type
                    it[principalId] = assignment.principal.id
                }.single()
                .toAssignment()
        } catch (e: Exception) {
            // PK가 같은 (매장, 계정 종류, 계정 ID)의 중복 지정. 확인과 저장 사이에 같은 지정이 들어오는 경우도 여기서 막힌다.
            if (e.isUniqueViolationOf(PRIMARY_KEY)) throw StoreManagerAlreadyAssignedException(assignment.storeId, assignment.principal)
            throw e
        }

    override suspend fun exists(
        storeId: StoreId,
        principal: PrincipalKey,
    ): Boolean =
        StoreManagersTable
            .selectAll()
            .where {
                (StoreManagersTable.storeId eq storeId.value) and
                    (StoreManagersTable.principalType eq principal.type) and
                    (StoreManagersTable.principalId eq principal.id)
            }.limit(1)
            .firstOrNull() != null

    override suspend fun delete(
        storeId: StoreId,
        principal: PrincipalKey,
    ): Boolean =
        StoreManagersTable.deleteWhere {
            (StoreManagersTable.storeId eq storeId.value) and
                (StoreManagersTable.principalType eq principal.type) and
                (StoreManagersTable.principalId eq principal.id)
        } > 0

    override suspend fun findByStoreId(storeId: StoreId): List<StoreManagerAssignment> =
        StoreManagersTable
            .selectAll()
            .where { StoreManagersTable.storeId eq storeId.value }
            .orderBy(
                StoreManagersTable.createdAt to SortOrder.ASC,
                StoreManagersTable.principalType to SortOrder.ASC,
                StoreManagersTable.principalId to SortOrder.ASC,
            ).toList()
            .map { it.toAssignment() }

    override suspend fun findByPrincipal(principal: PrincipalKey): List<StoreManagerAssignment> =
        StoreManagersTable
            .selectAll()
            .where { (StoreManagersTable.principalType eq principal.type) and (StoreManagersTable.principalId eq principal.id) }
            .orderBy(
                StoreManagersTable.createdAt to SortOrder.ASC,
                StoreManagersTable.storeId to SortOrder.ASC,
            ).toList()
            .map { it.toAssignment() }

    private fun ResultRow.toAssignment(): StoreManagerAssignment =
        StoreManagerAssignment.reconstitute(
            storeId = StoreId(this[StoreManagersTable.storeId]),
            principal = PrincipalKey(this[StoreManagersTable.principalType], this[StoreManagersTable.principalId]),
            assignedAt = this[StoreManagersTable.createdAt].toInstant(),
        )

    private companion object {
        /** (매장, 계정 종류, 계정 ID) 기본 키(V2__create_store_managers.sql) */
        const val PRIMARY_KEY = "store_managers_pkey"
    }
}
