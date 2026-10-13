package com.dozycoffee.store.management.adapter.inbound.web.response

import com.dozycoffee.store.management.application.port.inbound.result.StoreResult
import java.time.LocalDate

/** 매니저 화면의 매장 응답(docs/api/store.md `GET /stores/{storeId}`). [StoreResponse]에서 `version`을 뺀 것이다. */
data class StoreViewResponse(
    val storeId: Long,
    val code: String,
    val name: String,
    val address: AddressResponse,
    val phone: String,
    val status: String,
    val openingDate: LocalDate,
    val closingDate: LocalDate?,
) {
    companion object {
        fun from(result: StoreResult): StoreViewResponse =
            StoreViewResponse(
                storeId = result.storeId.value,
                code = result.code,
                name = result.name,
                address = AddressResponse.from(result.address),
                phone = result.phone,
                status = result.status.name,
                openingDate = result.openingDate,
                closingDate = result.closingDate,
            )
    }
}
