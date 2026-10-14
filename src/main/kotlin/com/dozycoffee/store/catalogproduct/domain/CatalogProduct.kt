package com.dozycoffee.store.catalogproduct.domain

import com.dozycoffee.store.catalogproduct.domain.event.CatalogProductScopeChanged
import com.dozycoffee.store.core.AggregateRoot
import com.dozycoffee.store.core.ProductId
import com.dozycoffee.store.core.Sku
import java.time.Instant

/**
 * Catalog 상품 중 노출 판단에 필요한 상태만 비춘 복제본(요구사항 7.1, ADR-0002). 이름·가격 같은 표시용 정보는 갖지 않는다.
 * 값은 Catalog 이벤트로만 바뀌고 Store의 어떤 요청도 직접 바꾸지 않는다.
 *
 * 처음 복제할 때는 [replicate]로 만들고, 저장소에서 복원할 때는 [reconstitute]로 만든다.
 */
class CatalogProduct private constructor(
    val productId: ProductId,
    val sku: Sku?,
    val status: CatalogProductStatus,
    val scope: StoreScope,
    val tracksInventory: Boolean,
    val sequence: Long,
    val deleted: Boolean,
    val lastEventAt: Instant,
) : AggregateRoot() {
    init {
        require(sequence >= 0) { "순번은 0 이상이어야 합니다: 상품 $productId, 순번 $sequence" }
    }

    companion object {
        /** 처음 복제한다. 스냅샷의 상태 그대로 만들고, 판매 범위 정리가 처음 복제에서도 돌도록 [CatalogProductScopeChanged]를 쌓는다. */
        fun replicate(snapshot: CatalogProductSnapshot): CatalogProduct =
            CatalogProduct(
                productId = snapshot.productId,
                sku = snapshot.sku,
                status = snapshot.status,
                scope = snapshot.scope,
                tracksInventory = snapshot.tracksInventory,
                sequence = snapshot.sequence,
                deleted = false,
                lastEventAt = snapshot.occurredAt,
            ).also { it.registerEvent(CatalogProductScopeChanged(it.productId, it.scope)) }

        /** 저장된 복제본을 복원한다. 이벤트를 쌓지 않는다. */
        fun reconstitute(
            productId: ProductId,
            sku: Sku?,
            status: CatalogProductStatus,
            scope: StoreScope,
            tracksInventory: Boolean,
            sequence: Long,
            deleted: Boolean,
            lastEventAt: Instant,
        ): CatalogProduct = CatalogProduct(productId, sku, status, scope, tracksInventory, sequence, deleted, lastEventAt)
    }
}
