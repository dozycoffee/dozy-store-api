package com.dozycoffee.store.catalogproduct.application.port.outbound

import com.dozycoffee.store.catalogproduct.domain.CatalogProduct
import com.dozycoffee.store.core.ProductId

/**
 * Catalog 상품 복제본 저장소. 트랜잭션은 열지 않는다. application 서비스의 `inTransaction` 안에서 호출된다.
 *
 * 같은 상품은 상품 행 단위로 직렬화한다(docs/erd.md 동시성 처리). 이벤트 반영은 [findByIdForUpdate], 매니저의 설정 변경은
 * [findByIdForShare]로 읽는다. 잠그는 순서는 항상 복제본 → 매장 설정이다.
 */
interface CatalogProductRepository {
    /** 상품 행을 `SELECT … FOR UPDATE`로 잠그고 읽는다. 없으면 `null`이다. 이벤트 반영이 쓴다. */
    suspend fun findByIdForUpdate(productId: ProductId): CatalogProduct?

    /**
     * 상품 행을 `SELECT … FOR SHARE`로 잠그고 읽는다. 없으면 `null`이다.
     * 읽는 동안 이 상품의 판매 범위가 바뀌지 않는다(`FOR UPDATE`와 서로 기다린다). 여러 요청이 동시에 읽을 수는 있다.
     */
    suspend fun findByIdForShare(productId: ProductId): CatalogProduct?

    /**
     * 상품이 없으면 넣고 `true`를, 같은 상품 ID가 이미 있으면 아무것도 바꾸지 않고 `false`를 돌려준다.
     * 동시에 같은 상품을 넣어도 하나만 `true`다. 이미 있으면 호출한 쪽이 [findByIdForUpdate]로 다시 잠그고 반영한다.
     *
     * 같은 상품 ID가 없는데 넣지 못했다면 다른 상품이 같은 SKU를 쓰는 것이다. Catalog의 계약 위반이므로 거부한다.
     */
    suspend fun insertIfAbsent(product: CatalogProduct): Boolean

    /**
     * 이미 있는 복제본의 바뀐 값을 저장한다. 판매 범위의 대상 매장은 지우고 다시 넣는다.
     * 재고 추적 여부는 바뀌지 않는 값이라 저장하지 않는다. [findByIdForUpdate]로 잠근 복제본을 저장하는 것이 전제다.
     * 저장된 복제본이 없으면 거부한다.
     */
    suspend fun save(product: CatalogProduct)
}
