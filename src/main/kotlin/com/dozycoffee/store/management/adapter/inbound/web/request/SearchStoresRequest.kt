package com.dozycoffee.store.management.adapter.inbound.web.request

import com.dozycoffee.store.common.paging.PageRequest
import com.dozycoffee.store.common.web.RequestValues
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.application.port.inbound.command.SearchStoresQuery
import com.dozycoffee.store.management.application.port.inbound.result.StoreStatusResult
import com.dozycoffee.store.management.domain.store.StoreInputLimits

/** 매장 목록 검색의 쿼리 파라미터(docs/api/store.md). 모두 문자열로 받아 [toQuery]가 검증한다. */
data class SearchStoresRequest(
    val ids: String?,
    val status: String?,
    val sido: String?,
    val sigungu: String?,
    val q: String?,
    val page: String?,
    val size: String?,
) {
    fun toQuery(): SearchStoresQuery =
        SearchStoresQuery(
            ids = RequestValues.positiveLongs("ids", ids, SearchStoresQuery.MAX_IDS).map { StoreId(it) }.toSet(),
            statuses = RequestValues.enums("status", status, StoreStatusResult::class.java),
            sido = RequestValues.optionalText("sido", sido, StoreInputLimits.SIDO_MAX),
            sigungu = RequestValues.optionalText("sigungu", sigungu, StoreInputLimits.SIGUNGU_MAX),
            nameContains = RequestValues.optionalText("q", q, StoreInputLimits.NAME_MAX),
            page =
                PageRequest(
                    page = RequestValues.int("page", page, default = 0, range = 0..Int.MAX_VALUE),
                    size = RequestValues.int("size", size, default = PageRequest.DEFAULT_SIZE, range = 1..PageRequest.MAX_SIZE),
                ),
        )
}
