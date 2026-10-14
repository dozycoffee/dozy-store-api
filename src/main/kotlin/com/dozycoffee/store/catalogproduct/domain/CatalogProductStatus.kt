package com.dozycoffee.store.catalogproduct.domain

/**
 * Catalog 상품의 상태(요구사항 7.1). 상태는 Catalog가 정하므로 Store는 전이를 검증하지 않고 이벤트로 받은 값으로 교체할 뿐이다.
 */
enum class CatalogProductStatus {
    /** 출시 전. 매장 목록과 손님 메뉴에 나오지 않는다(요구사항 5.1). */
    DRAFT,

    /** 판매 중. 노출 판단 1단계를 통과한다(요구사항 4장). */
    ACTIVE,

    /** 단종. 모든 매장에서 비노출이지만 매장의 개별 설정은 보존한다(요구사항 3.5). */
    DISCONTINUED,
}
