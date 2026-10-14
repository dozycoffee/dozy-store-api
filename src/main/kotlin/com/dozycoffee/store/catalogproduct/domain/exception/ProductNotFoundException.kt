package com.dozycoffee.store.catalogproduct.domain.exception

import com.dozycoffee.store.core.DomainException
import com.dozycoffee.store.core.ProductId

/** 이 매장에서 다룰 수 없는 상품이다. 없는 상품, 삭제된 상품, 판매 범위 밖의 상품을 구분하지 않는다. */
class ProductNotFoundException(
    productId: ProductId,
) : DomainException(CatalogProductErrorCode.PRODUCT_NOT_FOUND, "상품을 찾을 수 없습니다: $productId")
