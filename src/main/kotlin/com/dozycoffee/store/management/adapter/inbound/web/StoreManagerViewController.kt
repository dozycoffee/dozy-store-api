package com.dozycoffee.store.management.adapter.inbound.web

import com.dozycoffee.auth.core.AuthenticatedPrincipal
import com.dozycoffee.auth.starter.CurrentPrincipal
import com.dozycoffee.store.common.security.StoreAuthorization
import com.dozycoffee.store.common.web.ApiPaths
import com.dozycoffee.store.common.web.RequestValues
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.adapter.inbound.web.response.ManagedStoreResponse
import com.dozycoffee.store.management.adapter.inbound.web.response.StoreViewResponse
import com.dozycoffee.store.management.application.port.inbound.CheckStoreAccessUseCase
import com.dozycoffee.store.management.application.port.inbound.FindManagedStoresUseCase
import com.dozycoffee.store.management.application.port.inbound.GetStoreUseCase
import com.dozycoffee.store.management.application.port.inbound.Requester
import com.dozycoffee.store.management.application.port.inbound.StoreAccessMode
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 매장 매니저(점주, 직영점 점장)가 쓰는 매장 API(docs/api/store.md). 경로 인가는 계정 종류와 역할만 거르고,
 * 어느 매장을 다룰 수 있는지는 [CheckStoreAccessUseCase]가 판단한다(요구사항 2장).
 */
@RestController
@RequestMapping(ApiPaths.V1)
class StoreManagerViewController(
    private val checkAccess: CheckStoreAccessUseCase,
    private val getStore: GetStoreUseCase,
    private val findManagedStores: FindManagedStoresUseCase,
) {
    /** 한 매장의 정보. 폐점한 매장도 조회된다. 없는 매장과 접근할 수 없는 매장은 같은 404다. 매니저는 고칠 수 없으므로 `ETag`는 없다. */
    @PreAuthorize(StoreAuthorization.MANAGER_OR_ADMIN_OR_VIEWER)
    @GetMapping("/stores/{storeId}")
    suspend fun get(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @PathVariable storeId: String,
    ): StoreViewResponse {
        val id = StoreId(RequestValues.positiveLong("storeId", storeId))
        checkAccess.check(Requester.from(principal), id, StoreAccessMode.READ)
        return StoreViewResponse.from(getStore.get(id))
    }

    /** 내가 매니저로 지정된 매장(폐점 포함). 본사 권한이 있어도 자기가 지정된 매장만 돌려준다. 페이징하지 않는다. */
    @PreAuthorize(StoreAuthorization.MANAGER)
    @GetMapping("/me/stores")
    suspend fun listMine(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
    ): List<ManagedStoreResponse> = findManagedStores.find(Requester.from(principal)).map(ManagedStoreResponse::from)
}
