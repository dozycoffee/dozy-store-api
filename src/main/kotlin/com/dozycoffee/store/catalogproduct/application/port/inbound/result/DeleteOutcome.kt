package com.dozycoffee.store.catalogproduct.application.port.inbound.result

/** 상품 삭제를 반영한 결과. 컨슈머가 로그와 지표에 쓴다. */
enum class DeleteOutcome {
    /** 삭제로 표시했다. */
    DELETED,

    /** 이미 삭제된 복제본이다. 아무것도 바꾸지 않았다. */
    ALREADY_DELETED,

    /**
     * 복제본이 없는 상품이다. 아무것도 바꾸지 않았다.
     * 삭제 표시 행을 만들려면 상태·판매 범위·순번 같은 값이 필요한데 삭제 이벤트에는 없기 때문이다(ERD `catalog_products`의 NOT NULL).
     */
    UNKNOWN_PRODUCT,
}
