package com.dozycoffee.store.catalogproduct.adapter.outbound.persistence

import com.dozycoffee.store.catalogproduct.domain.CatalogProductStatus
import com.dozycoffee.store.common.exposed.auditTimestamp
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** `catalog_products` 테이블 정의. 마이그레이션(`V3__create_catalog_products.sql`)과 같은 이름·옵션으로 쓴다(docs/architecture/persistence.md). */
object CatalogProductsTable : Table("catalog_products") {
    /** Catalog가 발급한 상품 ID. identity가 아니다. */
    val productId = long("product_id")
    val sku = varchar("sku", 255).nullable()
    val status = enumerationByName("status", 20, CatalogProductStatus::class)
    val storeScope = enumerationByName("store_scope", 20, StoreScopeKind::class)
    val tracksInventory = bool("tracks_inventory")
    val sequence = long("sequence")
    val deleted = bool("deleted").default(false)
    val lastEventAt = timestampWithTimeZone("last_event_at")
    val createdAt = auditTimestamp("created_at")
    val updatedAt = auditTimestamp("updated_at")

    override val primaryKey = PrimaryKey(productId, name = "catalog_products_pkey")

    init {
        uniqueIndex("catalog_products_sku_key", sku)
        // 매장 상품 목록·손님 메뉴에서 ACTIVE 상품 고르기
        index("catalog_products_status_idx", false, status, filterCondition = { deleted eq false })

        // CHECK는 이름만 비교된다. 식은 마이그레이션이 원본이고 통합 테스트가 DB에서 확인한다.
        check("catalog_products_status_check") { status inList CatalogProductStatus.entries }
        check("catalog_products_store_scope_check") { storeScope inList StoreScopeKind.entries }
    }
}
