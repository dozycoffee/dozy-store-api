package com.dozycoffee.store.catalogproduct.domain.event

import com.dozycoffee.store.core.DomainEvent
import com.dozycoffee.store.core.ProductId

/** 복제본이 삭제로 표시됐다. 구독 측이 그 상품의 매장 데이터(진열 설정, 판매 가능 여부)를 지운다(요구사항 3.5). */
data class CatalogProductDeleted(
    val productId: ProductId,
) : DomainEvent
