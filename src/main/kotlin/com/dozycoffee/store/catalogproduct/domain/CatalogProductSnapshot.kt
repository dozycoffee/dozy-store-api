package com.dozycoffee.store.catalogproduct.domain

import com.dozycoffee.store.core.ProductId
import com.dozycoffee.store.core.Sku
import java.time.Instant

/**
 * Catalog가 알려 준 상품 상태 전체(요구사항 7.1). 이벤트 종류와 관계없이 이 값으로 복제본을 교체한다.
 * 메시지 형식은 어댑터가 이 타입으로 번역한다.
 *
 * @property sku SKU가 없는 상품일 수 있다.
 * @property sequence 같은 상품의 이벤트끼리 뒤에 일어난 것이 항상 큰 순번. 오래된 이벤트를 거르는 기준이다.
 * @property occurredAt 이벤트 발생 시각. 기록용이며 순서 기준이 아니다.
 */
data class CatalogProductSnapshot(
    val productId: ProductId,
    val sku: Sku?,
    val status: CatalogProductStatus,
    val scope: StoreScope,
    val tracksInventory: Boolean,
    val sequence: Long,
    val occurredAt: Instant,
)
