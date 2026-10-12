package com.dozycoffee.store.management.adapter.inbound.web.response

import com.dozycoffee.store.management.application.port.inbound.result.AddressResult
import com.dozycoffee.store.management.application.port.inbound.result.StoreResult
import java.time.LocalDate

/** 매장 응답(docs/api/store.md `Store`). 본사 경로(`/admin` 아래)의 응답이라 `version`을 담는다. */
data class StoreResponse(
    val storeId: Long,
    val code: String,
    val name: String,
    val address: AddressResponse,
    val phone: String,
    val status: String,
    val openingDate: LocalDate,
    val closingDate: LocalDate?,
    val version: Long,
) {
    companion object {
        fun from(result: StoreResult): StoreResponse =
            StoreResponse(
                storeId = result.storeId.value,
                code = result.code,
                name = result.name,
                address = AddressResponse.from(result.address),
                phone = result.phone,
                status = result.status.name,
                openingDate = result.openingDate,
                closingDate = result.closingDate,
                version = result.version,
            )
    }
}

data class AddressResponse(
    val roadAddress: String,
    val detailAddress: String?,
    val postalCode: String,
    val sido: String,
    val sigungu: String,
) {
    companion object {
        fun from(result: AddressResult): AddressResponse =
            AddressResponse(result.roadAddress, result.detailAddress, result.postalCode, result.sido, result.sigungu)
    }
}
