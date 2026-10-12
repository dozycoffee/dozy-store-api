package com.dozycoffee.store.management.application.port.inbound.command

import com.dozycoffee.store.common.paging.PageRequest
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.application.port.inbound.result.StoreStatusResult
import com.dozycoffee.store.management.domain.store.StoreInputLimits

/**
 * 매장 목록 검색 조건(요구사항 1.4). 주어진 조건은 모두 만족해야 한다. 조건을 주지 않은 항목은 거르지 않는다.
 * 개수 상한은 web이 요청 오류로 먼저 거르고, 여기서는 호출하는 코드의 실수를 한 번 더 막는다.
 */
data class SearchStoresQuery(
    /** 매장 ID로 거른다. 없는 ID는 오류 없이 빠진다. 최대 [MAX_IDS]개다. */
    val ids: Set<StoreId> = emptySet(),
    /** 상태로 거른다. 비어 있으면 `CLOSED`를 뺀 모든 상태이고, `CLOSED`를 지정하면 폐점한 매장도 나온다. */
    val statuses: Set<StoreStatusResult> = emptySet(),
    val sido: String? = null,
    val sigungu: String? = null,
    /** 매장명 부분 일치. 대소문자를 무시하고 `%`, `_`는 글자 그대로 찾는다. 길이는 매장명 상한을 넘을 수 없다. */
    val nameContains: String? = null,
    val page: PageRequest = PageRequest(),
) {
    init {
        require(ids.size <= MAX_IDS) { "ids는 최대 ${MAX_IDS}개입니다: ${ids.size}" }
        require(sido == null || sido.isNotBlank()) { "시·도는 비어 있을 수 없습니다" }
        require(sigungu == null || sigungu.isNotBlank()) { "시·군·구는 비어 있을 수 없습니다" }
        require(nameContains == null || nameContains.isNotBlank()) { "검색할 매장명은 비어 있을 수 없습니다" }
        require(nameContains == null || nameContains.length <= StoreInputLimits.NAME_MAX) {
            "검색할 매장명은 ${StoreInputLimits.NAME_MAX}자 이하여야 합니다: 현재 ${nameContains?.length}자"
        }
    }

    companion object {
        const val MAX_IDS = 100
    }
}
