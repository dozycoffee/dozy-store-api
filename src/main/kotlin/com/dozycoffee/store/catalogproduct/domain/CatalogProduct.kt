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
 * 이벤트는 [apply]로 반영한다. 변경 메서드는 규칙을 모두 확인한 뒤에 값을 바꾸므로, 거부되면 상태가 그대로다.
 */
class CatalogProduct private constructor(
    val productId: ProductId,
    sku: Sku?,
    status: CatalogProductStatus,
    scope: StoreScope,
    val tracksInventory: Boolean,
    sequence: Long,
    val deleted: Boolean,
    lastEventAt: Instant,
) : AggregateRoot() {
    var sku: Sku? = sku
        private set

    var status: CatalogProductStatus = status
        private set

    var scope: StoreScope = scope
        private set

    /** 마지막으로 반영한 이벤트의 순번. 이보다 크지 않은 이벤트는 무시한다. */
    var sequence: Long = sequence
        private set

    /** 마지막으로 반영한 이벤트의 발생 시각. 기록용이며 순서 기준이 아니다. */
    var lastEventAt: Instant = lastEventAt
        private set

    init {
        require(sequence >= 0) { "순번은 0 이상이어야 합니다: 상품 $productId, 순번 $sequence" }
    }

    /**
     * Catalog 이벤트의 상태 전체를 반영한다(요구사항 3.5, 7.1). 종류와 관계없이 같은 방식이다.
     *
     * 순번이 복제본보다 클 때만 상태 전체로 교체한다. 같거나 작으면(오래됐거나 중복) 아무것도 바꾸지 않는다.
     * 삭제된 복제본은 되살아나지 않으므로 반영하지 않는다.
     * 반영할 이벤트가 재고 추적 여부를 바꾸면 Catalog의 계약 위반이다(`check`). 이때도 값은 하나도 바뀌지 않는다.
     * 판매 범위가 이전과 다를 때만 [CatalogProductScopeChanged]를 쌓는다.
     */
    fun apply(snapshot: CatalogProductSnapshot): CatalogProductApplyResult {
        require(snapshot.productId == productId) { "다른 상품의 상태입니다: 복제본 $productId, 스냅샷 ${snapshot.productId}" }
        if (deleted || snapshot.sequence <= sequence) return CatalogProductApplyResult.NOT_APPLIED
        check(snapshot.tracksInventory == tracksInventory) {
            "재고 추적 여부는 바뀔 수 없습니다: 상품 $productId, 복제본 $tracksInventory, 이벤트 ${snapshot.tracksInventory} (Catalog의 계약 위반)"
        }

        val scopeChanged = snapshot.scope != scope
        sku = snapshot.sku
        status = snapshot.status
        scope = snapshot.scope
        sequence = snapshot.sequence
        lastEventAt = snapshot.occurredAt
        if (scopeChanged) registerEvent(CatalogProductScopeChanged(productId, scope))
        return CatalogProductApplyResult(applied = true, scopeChanged = scopeChanged)
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
