package com.dozycoffee.store.catalogproduct.application.port.inbound

import com.dozycoffee.store.catalogproduct.application.port.inbound.result.SellableProductResult
import com.dozycoffee.store.core.StoreId

interface ListSellableProductsUseCase {
    /**
     * 이 매장에서 판매할 수 있는 상품의 목록(요구사항 5.1, 5.2)을 상품 ID 순(본사 등록 순, 요구사항 3.3)으로 돌려준다.
     * `ACTIVE`이고 삭제되지 않았으며 판매 범위가 이 매장을 포함하는 상품이다.
     *
     * - `Draft`는 출시 전 상품이 매장에 먼저 알려지지 않게 하려고, `Discontinued`는 판매 목록에서 제외하려고 포함하지 않는다.
     *   단종된 상품의 매장 설정은 `storeproduct`에 보존되어 다시 활성화되면 그대로 쓰인다(요구사항 3.5).
     * - 읽기 전용이라 상품 행을 잠그지 않는다. 상품 상태와 판매 범위는 이벤트로 복제한 값이므로 읽는 시점의 값이다.
     * - 매장이 있는지, 영업 중인지는 보지 않는다. 노출 판단 0단계와 접근 확인은 호출하는 쪽의 일이다.
     */
    suspend fun list(storeId: StoreId): List<SellableProductResult>
}
