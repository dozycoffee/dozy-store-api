package com.dozycoffee.store.management.adapter.inbound.web

import com.dozycoffee.store.common.security.StoreAuthorization
import com.dozycoffee.store.common.web.ApiPaths
import com.dozycoffee.store.common.web.PageResponse
import com.dozycoffee.store.common.web.RequestValues
import com.dozycoffee.store.common.web.VersionHeaders
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.adapter.inbound.web.request.RegisterStoreRequest
import com.dozycoffee.store.management.adapter.inbound.web.request.SearchStoresRequest
import com.dozycoffee.store.management.adapter.inbound.web.response.StoreResponse
import com.dozycoffee.store.management.application.port.inbound.GetStoreUseCase
import com.dozycoffee.store.management.application.port.inbound.RegisterStoreUseCase
import com.dozycoffee.store.management.application.port.inbound.SearchStoresUseCase
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/** 본사 직원이 쓰는 매장 API(docs/api/store.md). 호출자의 역할과 계정 종류는 `@PreAuthorize`가 검사한다. */
@RestController
@RequestMapping("${ApiPaths.V1}/admin/stores")
class StoreAdminController(
    private val registerStore: RegisterStoreUseCase,
    private val getStore: GetStoreUseCase,
    private val searchStores: SearchStoresUseCase,
) {
    @PreAuthorize(StoreAuthorization.ADMIN)
    @PostMapping
    suspend fun register(
        @RequestBody request: RegisterStoreRequest,
    ): ResponseEntity<StoreResponse> {
        val store = StoreResponse.from(registerStore.register(request.toCommand()))
        return ResponseEntity
            .created(URI.create("${ApiPaths.V1}/admin/stores/${store.storeId}"))
            .eTag(VersionHeaders.etag(store.version))
            .body(store)
    }

    /** 폐점한 매장도 조회된다. 응답의 `ETag`는 매장 정보 수정(`If-Match`)에 쓰는 버전이다. */
    @PreAuthorize(StoreAuthorization.ADMIN_OR_VIEWER)
    @GetMapping("/{storeId}")
    suspend fun get(
        @PathVariable storeId: String,
    ): ResponseEntity<StoreResponse> {
        val store = StoreResponse.from(getStore.get(StoreId(RequestValues.positiveLong("storeId", storeId))))
        return ResponseEntity.ok().eTag(VersionHeaders.etag(store.version)).body(store)
    }

    /** 상태를 주지 않으면 폐점한 매장은 뺀다. 매장명 가나다순이다. 목록에는 `ETag`를 붙이지 않는다(건마다 본문에 `version`이 있다). */
    @PreAuthorize(StoreAuthorization.ADMIN_OR_VIEWER)
    @GetMapping
    suspend fun search(
        @RequestParam(required = false) ids: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) sido: String?,
        @RequestParam(required = false) sigungu: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) page: String?,
        @RequestParam(required = false) size: String?,
    ): PageResponse<StoreResponse> =
        PageResponse.from(
            searchStores.search(SearchStoresRequest(ids, status, sido, sigungu, q, page, size).toQuery()),
            StoreResponse::from,
        )
}
