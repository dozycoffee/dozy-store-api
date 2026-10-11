package com.dozycoffee.store.management.adapter.outbound.persistence

import com.dozycoffee.store.common.exposed.isUniqueViolationOf
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.application.port.outbound.StoreRepository
import com.dozycoffee.store.management.domain.store.Address
import com.dozycoffee.store.management.domain.store.NewStore
import com.dozycoffee.store.management.domain.store.Store
import com.dozycoffee.store.management.domain.store.StoreCode
import com.dozycoffee.store.management.domain.store.StoreStatus
import com.dozycoffee.store.management.domain.store.exception.StoreNameDuplicatedException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.r2dbc.insertReturning
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.springframework.stereotype.Repository

@Repository
class ExposedStoreRepository : StoreRepository {
    override suspend fun insert(store: NewStore): Store =
        try {
            StoresTable
                .insertReturning {
                    it[code] = store.code.value
                    it[name] = store.name
                    it[roadAddress] = store.address.roadAddress
                    it[detailAddress] = store.address.detailAddress
                    it[postalCode] = store.address.postalCode
                    it[sido] = store.address.sido
                    it[sigungu] = store.address.sigungu
                    it[phone] = store.phone
                    it[openingDate] = store.openingDate
                }.single()
                .toStore()
        } catch (e: Exception) {
            if (e.isUniqueViolationOf(ACTIVE_NAME_UNIQUE)) throw StoreNameDuplicatedException(store.name)
            throw e
        }

    override suspend fun findById(id: StoreId): Store? =
        StoresTable
            .selectAll()
            .where { StoresTable.id eq id.value }
            .firstOrNull()
            ?.toStore()

    override suspend fun existsActiveByName(name: String): Boolean =
        StoresTable
            .selectAll()
            .where { (StoresTable.name eq name) and (StoresTable.status neq StoreStatus.CLOSED) }
            .limit(1)
            .firstOrNull() != null

    private fun ResultRow.toStore(): Store =
        Store.reconstitute(
            id = StoreId(this[StoresTable.id]),
            code = StoreCode(this[StoresTable.code]),
            name = this[StoresTable.name],
            address =
                Address(
                    roadAddress = this[StoresTable.roadAddress],
                    detailAddress = this[StoresTable.detailAddress],
                    postalCode = this[StoresTable.postalCode],
                    sido = this[StoresTable.sido],
                    sigungu = this[StoresTable.sigungu],
                ),
            phone = this[StoresTable.phone],
            status = this[StoresTable.status],
            openingDate = this[StoresTable.openingDate],
            closingDate = this[StoresTable.closingDate],
            version = this[StoresTable.version],
        )

    private companion object {
        /** 폐점하지 않은 매장 사이의 이름 유일 인덱스(V1__create_stores.sql) */
        const val ACTIVE_NAME_UNIQUE = "stores_name_active_key"
    }
}
