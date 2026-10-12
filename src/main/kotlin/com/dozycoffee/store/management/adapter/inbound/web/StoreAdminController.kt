package com.dozycoffee.store.management.adapter.inbound.web

import com.dozycoffee.store.common.security.StoreAuthorization
import com.dozycoffee.store.common.web.ApiPaths
import com.dozycoffee.store.common.web.PageResponse
import com.dozycoffee.store.common.web.RequestValues
import com.dozycoffee.store.common.web.VersionHeaders
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.adapter.inbound.web.request.SearchStoresRequest
import com.dozycoffee.store.management.adapter.inbound.web.request.StoreInfoRequest
import com.dozycoffee.store.management.adapter.inbound.web.response.StoreResponse
import com.dozycoffee.store.management.application.port.inbound.GetStoreUseCase
import com.dozycoffee.store.management.application.port.inbound.RegisterStoreUseCase
import com.dozycoffee.store.management.application.port.inbound.SearchStoresUseCase
import com.dozycoffee.store.management.application.port.inbound.UpdateStoreUseCase
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
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
    private val updateStore: UpdateStoreUseCase,
) {
    @PreAuthorize(StoreAuthorization.ADMIN)
    @PostMapping
    suspend fun register(
        @RequestBody request: StoreInfoRequest,
    ): ResponseEntity<StoreResponse> {
        val store = StoreResponse.from(registerStore.register(request.toRegisterCommand()))
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

    /**
     * 매장 정보 전체를 바꾼다(등록과 같은 본문). `If-Match`로 화면이 본 버전을 받아, 그 사이 다른 변경이 있었으면 `409 VERSION_CONFLICT`다.
     * 처리 순서는 인가 → 경로 변수 → `If-Match` → 본문 검증 → 유스케이스다.
     */
    @PreAuthorize(StoreAuthorization.ADMIN)
    @PutMapping("/{storeId}")
    suspend fun update(
        @PathVariable storeId: String,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestBody request: StoreInfoRequest,
    ): ResponseEntity<StoreResponse> {
        val id = StoreId(RequestValues.positiveLong("storeId", storeId))
        val expectedVersion = VersionHeaders.parseIfMatch(ifMatch)
        val store = StoreResponse.from(updateStore.update(request.toUpdateCommand(id, expectedVersion)))
        return ResponseEntity.ok().eTag(VersionHeaders.etag(store.version)).body(store)
    }
}
