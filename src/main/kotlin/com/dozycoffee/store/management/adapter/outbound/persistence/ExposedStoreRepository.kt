package com.dozycoffee.store.management.adapter.outbound.persistence

import com.dozycoffee.store.common.exposed.dbNow
import com.dozycoffee.store.common.exposed.isUniqueViolationOf
import com.dozycoffee.store.common.paging.PageResult
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.core.VersionConflictException
import com.dozycoffee.store.management.application.port.outbound.StoreRepository
import com.dozycoffee.store.management.application.port.outbound.StoreSearchCriteria
import com.dozycoffee.store.management.domain.store.Address
import com.dozycoffee.store.management.domain.store.NewStore
import com.dozycoffee.store.management.domain.store.Store
import com.dozycoffee.store.management.domain.store.StoreCode
import com.dozycoffee.store.management.domain.store.StoreStatus
import com.dozycoffee.store.management.domain.store.exception.StoreNameDuplicatedException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
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

    override suspend fun findAllByIds(ids: Collection<StoreId>): List<Store> {
        if (ids.isEmpty()) return emptyList()
        return StoresTable
            .selectAll()
            .where { StoresTable.id inList ids.map { it.value } }
            .orderBy(
                closedLast to SortOrder.ASC,
                StoresTable.name.koreanCollated() to SortOrder.ASC,
                StoresTable.id to SortOrder.ASC,
            ).toList()
            .map { it.toStore() }
    }

    override suspend fun search(criteria: StoreSearchCriteria): PageResult<Store> {
        val conditions = mutableListOf<Op<Boolean>>()
        if (criteria.ids.isNotEmpty()) conditions += StoresTable.id inList criteria.ids.map { it.value }
        conditions +=
            if (criteria.statuses.isEmpty()) {
                StoresTable.status neq StoreStatus.CLOSED
            } else {
                StoresTable.status inList criteria.statuses
            }
        criteria.sido?.let { conditions += StoresTable.sido eq it }
        criteria.sigungu?.let { conditions += StoresTable.sigungu eq it }
        criteria.nameContains?.let { conditions += StoresTable.name.lowerCase() like containing(it.lowercase()) }
        val matched = conditions.reduce { left, right -> left and right }

        val total = StoresTable.selectAll().where { matched }.count()
        val content =
            StoresTable
                .selectAll()
                .where { matched }
                .orderBy(StoresTable.name.koreanCollated() to SortOrder.ASC, StoresTable.id to SortOrder.ASC)
                .limit(criteria.page.size)
                .offset(criteria.page.offset)
                .toList()
                .map { it.toStore() }
        return PageResult(content, criteria.page.page, criteria.page.size, total)
    }

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

    /** 폐점한 매장을 뒤로 보내는 정렬 키(0 = 폐점하지 않음, 1 = 폐점). */
    private val closedLast: Expression<Int> =
        Case()
            .When(StoresTable.status eq StoreStatus.CLOSED, intLiteral(1))
            .Else(intLiteral(0))

    /** DB 기본 정렬 규칙은 한글을 가나다순으로 정렬하지 않으므로 한국어 ICU 규칙으로 정렬한다(docs/architecture/persistence.md). */
    private fun Expression<String>.koreanCollated(): Expression<String> {
        val column = this
        return object : Expression<String>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                queryBuilder {
                    append(column)
                    append(" COLLATE \"$KOREAN_COLLATION\"")
                }
            }
        }
    }

    /** `%`, `_`를 글자 그대로 찾는 부분 일치 패턴. 대소문자를 무시하려고 컬럼과 패턴을 모두 소문자로 맞춘다. */
    private fun containing(text: String): LikePattern = LikePattern("%", LIKE_ESCAPE) + LikePattern.ofLiteral(text, LIKE_ESCAPE) + "%"

    private companion object {
        const val LIKE_ESCAPE = '\\'
        const val KOREAN_COLLATION = "ko-x-icu"

        /** 폐점하지 않은 매장 사이의 이름 유일 인덱스(V1__create_stores.sql) */
        const val ACTIVE_NAME_UNIQUE = "stores_name_active_key"
    }
}
