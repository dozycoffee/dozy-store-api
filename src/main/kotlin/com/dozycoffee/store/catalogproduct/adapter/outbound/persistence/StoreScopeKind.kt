package com.dozycoffee.store.catalogproduct.adapter.outbound.persistence

/** `catalog_products.store_scope` 컬럼의 값. 도메인의 sealed `StoreScope`는 이 값과 대상 매장 테이블로 나뉘어 저장된다. */
enum class StoreScopeKind {
    ALL,
    LIMITED,
}
