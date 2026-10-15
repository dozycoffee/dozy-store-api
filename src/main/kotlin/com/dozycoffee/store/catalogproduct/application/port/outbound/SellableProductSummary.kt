package com.dozycoffee.store.catalogproduct.application.port.outbound

import com.dozycoffee.store.core.ProductId

/** 이 매장에서 판매할 수 있는 상품 목록의 한 건. 목록에 필요한 값만 읽고 복제본(`CatalogProduct`)으로 복원하지 않는다. */
data class SellableProductSummary(
    val productId: ProductId,
    val tracksInventory: Boolean,
)
