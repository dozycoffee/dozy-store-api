package com.dozycoffee.store.catalogproduct.application.port.inbound.result

/** 상품 이벤트를 반영한 결과. 컨슈머가 로그와 지표에 쓴다. */
enum class ApplyOutcome {
    /** 처음 보는 상품이라 복제했다. */
    REPLICATED,

    /** 순번이 복제본보다 커서 상태 전체로 교체했다. */
    APPLIED,

    /** 반영하지 않았다. 오래됐거나 중복된 이벤트이거나 삭제된 상품이다. 저장된 것은 그대로다. */
    IGNORED,
}
