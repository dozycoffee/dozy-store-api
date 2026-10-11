package com.dozycoffee.store.common.exposed

/**
 * 트랜잭션 단위 권고 잠금(`pg_advisory_xact_lock(용도, 대상 ID)`)의 첫 키. 용도끼리 겹치지 않게 여기에 모은다.
 * (docs/architecture/persistence.md 권고 잠금 네임스페이스)
 */
object AdvisoryLockNamespace {
    /** 매장 진열 순서 일괄 변경. 둘째 키는 storeId 하위 32비트 */
    const val STORE_DISPLAY_ORDER = 1

    /** (2단계, 예약) 재고 이벤트 보관과 상품 복제의 SKU 매칭. 둘째 키는 `hashtext(sku)`. 1단계에서는 쓰지 않는다 */
    const val SKU_INVENTORY_MATCH = 2
}
