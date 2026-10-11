package com.dozycoffee.store.management.adapter.outbound.persistence

import com.dozycoffee.store.common.exposed.dbNow
import com.dozycoffee.store.common.exposed.isUniqueViolationOf
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.core.VersionConflictException
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
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedStoreRepository : StoreRepository {
    override suspend fun insert(store: NewStore): Store =
        translatingNameViolation(store.name) {
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
        }

    override suspend fun findById(id: StoreId): Store? =
        StoresTable
            .selectAll()
            .where { StoresTable.id eq id.value }
            .firstOrNull()
            ?.toStore()

    override suspend fun findByIdForUpdate(id: StoreId): Store? =
        StoresTable
            .selectAll()
            .where { StoresTable.id eq id.value }
            .forUpdate()
            .firstOrNull()
            ?.toStore()

    override suspend fun save(store: Store): Store {
        val updated =
            translatingNameViolation(store.name) {
                StoresTable.update({ (StoresTable.id eq store.id.value) and (StoresTable.version eq store.version) }) {
                    it[name] = store.name
                    it[roadAddress] = store.address.roadAddress
                    it[detailAddress] = store.address.detailAddress
                    it[postalCode] = store.address.postalCode
                    it[sido] = store.address.sido
                    it[sigungu] = store.address.sigungu
                    it[phone] = store.phone
                    it[status] = store.status
                    it[openingDate] = store.openingDate
                    it[closingDate] = store.closingDate
                    it[version] = store.version + 1
                    it[updatedAt] = dbNow
                }
            }
        if (updated == 0) throw VersionConflictException(currentVersion(store.id))
        return checkNotNull(findById(store.id)) { "저장한 매장을 다시 읽지 못했습니다: ${store.id}" }
    }

    override suspend fun existsActiveByName(
        name: String,
        excluding: StoreId?,
    ): Boolean =
        StoresTable
            .selectAll()
            .where {
                val sameActiveName = (StoresTable.name eq name) and (StoresTable.status neq StoreStatus.CLOSED)
                if (excluding == null) sameActiveName else sameActiveName and (StoresTable.id neq excluding.value)
            }.limit(1)
            .firstOrNull() != null

    /** 충돌 응답에 담을 저장소의 현재 버전. 매장이 없으면 알 수 없다. */
    private suspend fun currentVersion(id: StoreId): Long? =
        StoresTable
            .select(StoresTable.version)
            .where { StoresTable.id eq id.value }
            .firstOrNull()
            ?.get(StoresTable.version)

    /** 폐점하지 않은 매장 사이의 이름 유일 위반(23505)을 [StoreNameDuplicatedException]으로 바꾼다. */
    private suspend fun <T> translatingNameViolation(
        name: String,
        block: suspend () -> T,
    ): T =
        try {
            block()
        } catch (e: Exception) {
            if (e.isUniqueViolationOf(ACTIVE_NAME_UNIQUE)) throw StoreNameDuplicatedException(name)
            throw e
        }

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
