package com.dozycoffee.store.management.application.port.outbound

import com.dozycoffee.store.common.paging.PageRequest
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.domain.store.StoreStatus

/** 저장소에 넘기는 매장 검색 조건. 조건을 주지 않은 항목은 거르지 않는다. */
data class StoreSearchCriteria(
    val ids: Set<StoreId> = emptySet(),
    /** 비어 있으면 `CLOSED`를 뺀 모든 상태 */
    val statuses: Set<StoreStatus> = emptySet(),
    val sido: String? = null,
    val sigungu: String? = null,
    /** 매장명 부분 일치. 대소문자를 무시하고 `%`, `_`는 글자 그대로 찾는다. */
    val nameContains: String? = null,
    val page: PageRequest = PageRequest(),
)
