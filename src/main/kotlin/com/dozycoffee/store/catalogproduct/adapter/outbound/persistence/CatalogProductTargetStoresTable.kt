package com.dozycoffee.store.catalogproduct.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/**
 * `catalog_product_target_stores` 테이블 정의. 복제한 판매 범위(`LIMITED`)의 대상 매장이다.
 * `store_id`는 Catalog가 보낸 값이라 FK가 없다. 복제본을 교체할 때 지우고 다시 넣으므로 감사 컬럼도 없다.
 */
object CatalogProductTargetStoresTable : Table("catalog_product_target_stores") {
    val productId =
        reference(
            "product_id",
            CatalogProductsTable.productId,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "catalog_product_target_stores_product_id_fkey",
        )
    val storeId = long("store_id")

    override val primaryKey = PrimaryKey(productId, storeId, name = "catalog_product_target_stores_pkey")

    init {
        // "이 매장이 대상인 상품" 조회
        index("catalog_product_target_stores_store_id_idx", false, storeId)
    }
}
