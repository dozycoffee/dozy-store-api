package com.dozycoffee.store.management.application.port.inbound

/** 매장에 대한 요청이 읽기인지 쓰기인지. 본사 조회 권한(`store:viewer`)은 읽기만 허용된다. */
enum class StoreAccessMode {
    READ,
    WRITE,
}
