package com.dozycoffee.store.management.adapter.outbound.persistence

import com.dozycoffee.store.common.exposed.auditTimestamp
import com.dozycoffee.store.management.domain.store.StoreStatus
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.date

/** `stores` 테이블 정의. 마이그레이션(`V1__create_stores.sql`)과 같은 이름·옵션으로 쓴다(docs/architecture/persistence.md). */
object StoresTable : Table("stores") {
    val id = long("id").autoIncrement()
    val code = varchar("code", 16)
    val name = varchar("name", 255)
    val roadAddress = varchar("road_address", 255)
    val detailAddress = varchar("detail_address", 255).nullable()
    val postalCode = varchar("postal_code", 255)
    val sido = varchar("sido", 255)
    val sigungu = varchar("sigungu", 255)
    val phone = varchar("phone", 255)
    val status = enumerationByName("status", 20, StoreStatus::class).default(StoreStatus.PREPARING)
    val openingDate = date("opening_date")
    val closingDate = date("closing_date").nullable()
    val version = long("version").default(0)
    val createdAt = auditTimestamp("created_at")
    val updatedAt = auditTimestamp("updated_at")

    override val primaryKey = PrimaryKey(id, name = "stores_pkey")

    init {
        uniqueIndex("stores_code_key", code)
        // 매장명은 폐점하지 않은 매장 사이에서 유일하다
        index("stores_name_active_key", true, name, filterCondition = { status neq StoreStatus.CLOSED })
        index("stores_sido_sigungu_idx", false, sido, sigungu)
        index("stores_status_idx", false, status)

        // CHECK는 이름만 비교된다. 식은 마이그레이션이 원본이고 Repository 통합 테스트가 DB에서 확인한다.
        check("stores_status_check") { status inList StoreStatus.entries }
        check("stores_closing_date_check") {
            ((status eq StoreStatus.CLOSED) and closingDate.isNotNull()) or ((status neq StoreStatus.CLOSED) and closingDate.isNull())
        }
    }
}
