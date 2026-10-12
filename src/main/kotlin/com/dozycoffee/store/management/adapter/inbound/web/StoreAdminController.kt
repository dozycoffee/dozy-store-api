package com.dozycoffee.store.management.adapter.inbound.web

import com.dozycoffee.store.common.security.StoreAuthorization
import com.dozycoffee.store.common.web.ApiPaths
import com.dozycoffee.store.common.web.VersionHeaders
import com.dozycoffee.store.management.adapter.inbound.web.request.RegisterStoreRequest
import com.dozycoffee.store.management.adapter.inbound.web.response.StoreResponse
import com.dozycoffee.store.management.application.port.inbound.RegisterStoreUseCase
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/** 본사 직원이 쓰는 매장 API(docs/api/store.md). 호출자의 역할과 계정 종류는 `@PreAuthorize`가 검사한다. */
@RestController
@RequestMapping("${ApiPaths.V1}/admin/stores")
class StoreAdminController(
    private val registerStore: RegisterStoreUseCase,
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
}
