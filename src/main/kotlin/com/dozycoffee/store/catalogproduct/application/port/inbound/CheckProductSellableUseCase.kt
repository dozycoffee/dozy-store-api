package com.dozycoffee.store.catalogproduct.application.port.inbound

import com.dozycoffee.store.catalogproduct.application.port.inbound.result.SellableProductResult
import com.dozycoffee.store.core.ProductId
import com.dozycoffee.store.core.StoreId

/**
 * 이 매장에서 상품을 다룰 수 있는지 확인한다(요구사항 3.1). 매니저가 진열 순서·노출 여부·수동 품절을 바꾸기 전에 쓴다.
 *
 * - 다룰 수 있는 상품은 삭제되지 않았고 판매 범위가 이 매장을 포함한다. 상품 상태는 보지 않는다. `Draft`·`Discontinued`여도 판매 범위에 있으면
 *   다룰 수 있고, 미리 바꿔 둔 설정은 활성화된 뒤에 그대로 쓰인다.
 * - 상품이 없거나, 삭제됐거나, 판매 범위 밖이면 구분하지 않고 `ProductNotFoundException`으로 거부한다. 상품 ID로 존재 여부나 판매 범위를
 *   알아낼 수 없게 하기 위해서다.
 * - 확인하는 동안 상품 행을 `FOR SHARE`로 잠근다. 잠금은 호출한 트랜잭션이 끝날 때까지 유지되므로, 확인 뒤 설정을 저장하는 사이에
 *   판매 범위 변경 이벤트의 정리가 끼어들지 않는다(ERD 동시성 처리). 호출하는 쪽은 자기 트랜잭션 안에서 불러야 한다.
 * - 매장이 있는지, 폐점했는지는 보지 않는다. `management`의 `CheckStoreAccessUseCase`가 판단한다.
 */
interface CheckProductSellableUseCase {
    /** 상품 하나를 확인한다. 다룰 수 없으면 `ProductNotFoundException`이다. */
    suspend fun check(
        storeId: StoreId,
        productId: ProductId,
    ): SellableProductResult

    /**
     * 여러 상품을 한 번에 확인한다. 하나라도 다룰 수 없으면 전체를 거부하고, 상품 ID가 가장 작은 어긋난 상품으로 `ProductNotFoundException`을
     * 던진다. 상품 ID 순서로 잠그고(교착 방지) 결과도 그 순서다. 같은 ID가 여러 번 와도 한 번만 확인하고, 빈 목록이면 빈 결과다.
     */
    suspend fun checkAll(
        storeId: StoreId,
        productIds: Collection<ProductId>,
    ): List<SellableProductResult>
}
