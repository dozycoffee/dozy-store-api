package com.dozycoffee.store.management.application.port.inbound.result

/** 매장의 영업 상태(요구사항 1.2). 다른 모듈이 domain 타입을 쓰지 않도록 Result 쪽에 따로 둔다. */
enum class StoreStatusResult {
    PREPARING,
    OPEN,
    SUSPENDED,
    CLOSED,
}
