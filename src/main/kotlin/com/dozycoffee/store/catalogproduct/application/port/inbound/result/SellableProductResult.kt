package com.dozycoffee.store.catalogproduct.application.port.inbound.result

import com.dozycoffee.store.core.ProductId

/**
 * 이 매장에서 다룰 수 있는 상품(요구사항 3.1).
 *
 * @property tracksInventory 재고 추적 상품인가. 1단계에서는 쓰지 않고, 2단계에서 판매 가능 여부의 출처를 정할 때 쓴다(요구사항 8.3).
 */
data class SellableProductResult(
    val productId: ProductId,
    val tracksInventory: Boolean,
)
