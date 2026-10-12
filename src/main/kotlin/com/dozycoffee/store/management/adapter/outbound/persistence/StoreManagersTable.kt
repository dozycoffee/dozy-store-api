package com.dozycoffee.store.management.adapter.outbound.persistence

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.store.common.exposed.auditTimestamp
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.java.javaUUID

/** `store_managers` 테이블 정의. 마이그레이션(`V2__create_store_managers.sql`)과 같은 이름·옵션으로 쓴다. */
object StoreManagersTable : Table("store_managers") {
    val storeId =
        reference(
            "store_id",
            StoresTable.id,
            onDelete = ReferenceOption.NO_ACTION,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "store_managers_store_id_fkey",
        )
    val principalType = enumerationByName("principal_type", 20, PrincipalType::class)
    val principalId = javaUUID("principal_id")

    /** 지정한 시각(API의 `assignedAt`) */
    val createdAt = auditTimestamp("created_at")

    override val primaryKey = PrimaryKey(storeId, principalType, principalId, name = "store_managers_pkey")

    init {
        index("store_managers_principal_idx", false, principalType, principalId)

        // CHECK는 이름만 비교된다. 식은 마이그레이션이 원본이고 Repository 통합 테스트가 DB에서 확인한다.
        check("store_managers_principal_type_check") { principalType inList listOf(PrincipalType.PARTNER, PrincipalType.EMPLOYEE) }
    }
}
