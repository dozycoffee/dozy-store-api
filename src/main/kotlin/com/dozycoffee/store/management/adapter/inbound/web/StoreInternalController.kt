package com.dozycoffee.store.management.adapter.inbound.web

import com.dozycoffee.store.common.security.StoreAuthorization
import com.dozycoffee.store.common.web.ApiPaths
import com.dozycoffee.store.management.adapter.inbound.web.request.AssignabilityCheckRequest
import com.dozycoffee.store.management.adapter.inbound.web.response.AssignabilityCheckResponse
import com.dozycoffee.store.management.application.port.inbound.CheckStoreAssignabilityUseCase
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 다른 서비스가 부르는 내부 API(docs/api/internal.md). 지금은 Catalog의 서비스 계정(`store:catalog_agent`)만 부른다. */
@RestController
@RequestMapping("${ApiPaths.V1}/internal")
class StoreInternalController(
    private val checkAssignability: CheckStoreAssignabilityUseCase,
) {
    /**
     * 판매 범위 대상으로 지정할 수 없는 매장(없는 매장, 폐점한 매장)을 돌려준다(요구사항 7.2).
     * 지정할 수 없는 매장이 있어도 오류가 아니다. 조회지만 목록이 길 수 있어 본문으로 받는다.
     */
    @PreAuthorize(StoreAuthorization.CATALOG_AGENT)
    @PostMapping("/stores/assignability-check")
    suspend fun check(
        @RequestBody request: AssignabilityCheckRequest,
    ): AssignabilityCheckResponse = AssignabilityCheckResponse.from(checkAssignability.check(request.toStoreIds()))
}
