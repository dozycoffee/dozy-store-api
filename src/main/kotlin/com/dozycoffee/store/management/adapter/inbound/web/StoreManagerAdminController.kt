package com.dozycoffee.store.management.adapter.inbound.web

import com.dozycoffee.store.common.security.StoreAuthorization
import com.dozycoffee.store.common.web.ApiPaths
import com.dozycoffee.store.common.web.RequestValues
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.adapter.inbound.web.request.AssignStoreManagerRequest
import com.dozycoffee.store.management.adapter.inbound.web.request.PrincipalKeyValues
import com.dozycoffee.store.management.adapter.inbound.web.response.StoreManagerResponse
import com.dozycoffee.store.management.application.port.inbound.AssignStoreManagerUseCase
import com.dozycoffee.store.management.application.port.inbound.GetStoreManagersByPrincipalUseCase
import com.dozycoffee.store.management.application.port.inbound.GetStoreManagersUseCase
import com.dozycoffee.store.management.application.port.inbound.UnassignStoreManagerUseCase
import com.dozycoffee.store.management.application.port.inbound.command.UnassignStoreManagerCommand
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * 본사 직원이 쓰는 매장 매니저 API(docs/api/manager.md). 쓰기(지정·해제)는 매장 관리 권한, 읽기는 매장 관리 또는 본사 조회 권한이다.
 * Store는 Auth를 호출하지 않아 계정이 실제로 있는지 확인하지 않고 principal key를 그대로 저장하고 돌려준다(요구사항 1.3).
 */
@RestController
@RequestMapping("${ApiPaths.V1}/admin")
class StoreManagerAdminController(
    private val assignManager: AssignStoreManagerUseCase,
    private val unassignManager: UnassignStoreManagerUseCase,
    private val getManagers: GetStoreManagersUseCase,
    private val getManagersByPrincipal: GetStoreManagersByPrincipalUseCase,
) {
    /** 매장의 매니저 목록. 지정한 순서이고 폐점한 매장도 조회된다. */
    @PreAuthorize(StoreAuthorization.ADMIN_OR_VIEWER)
    @GetMapping("/stores/{storeId}/managers")
    suspend fun list(
        @PathVariable storeId: String,
    ): List<StoreManagerResponse> = getManagers.getByStore(storeId(storeId)).map(StoreManagerResponse::from)

    /** 폐점한 매장에는 지정할 수 없다. 이미 지정된 계정이나 매니저로 지정할 수 없는 계정 종류(`system`, `customer`)는 거부한다. */
    @PreAuthorize(StoreAuthorization.ADMIN)
    @PostMapping("/stores/{storeId}/managers")
    suspend fun assign(
        @PathVariable storeId: String,
        @RequestBody request: AssignStoreManagerRequest,
    ): ResponseEntity<StoreManagerResponse> {
        val manager = StoreManagerResponse.from(assignManager.assign(request.toCommand(storeId(storeId))))
        val location = "${ApiPaths.V1}/admin/stores/${manager.storeId}/managers/${manager.principalType}:${manager.principalId}"
        return ResponseEntity.created(URI.create(location)).body(manager)
    }

    /** 폐점한 매장에서도 해제할 수 있다. 매장의 개별 설정은 바뀌지 않는다. */
    @PreAuthorize(StoreAuthorization.ADMIN)
    @DeleteMapping("/stores/{storeId}/managers/{principalKey}")
    suspend fun unassign(
        @PathVariable storeId: String,
        @PathVariable principalKey: String,
    ): ResponseEntity<Void> {
        unassignManager.unassign(UnassignStoreManagerCommand(storeId(storeId), PrincipalKeyValues.fromSub("principalKey", principalKey)))
        return ResponseEntity.noContent().build()
    }

    /** 한 계정이 매니저인 지정(폐점한 매장 포함). 탈퇴·퇴사한 계정의 남은 지정을 찾아 해제할 때 쓴다. */
    @PreAuthorize(StoreAuthorization.ADMIN_OR_VIEWER)
    @GetMapping("/store-managers")
    suspend fun listByPrincipal(
        @RequestParam(required = false) principalKey: String?,
    ): List<StoreManagerResponse> =
        getManagersByPrincipal
            .getByPrincipal(PrincipalKeyValues.assignableFromSub("principalKey", principalKey))
            .map(StoreManagerResponse::from)

    private fun storeId(value: String): StoreId = StoreId(RequestValues.positiveLong("storeId", value))
}
