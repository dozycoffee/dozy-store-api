package com.dozycoffee.store.catalogproduct.domain.event

import com.dozycoffee.store.catalogproduct.domain.StoreScope
import com.dozycoffee.store.core.DomainEvent
import com.dozycoffee.store.core.ProductId

/**
 * 복제본의 판매 범위가 바뀌었다(처음 복제 포함). 구독 측이 새 판매 범위에 들지 않는 매장의 설정을 정리한다(요구사항 3.5).
 * 이전 판매 범위와 비교하지 않으므로 이전 값은 담지 않는다.
 */
data class CatalogProductScopeChanged(
    val productId: ProductId,
    val scope: StoreScope,
) : DomainEvent
