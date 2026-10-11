package com.dozycoffee.store.management.application.port.inbound.result

import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.domain.store.Store
import java.time.LocalDate

/** 매장 조회·등록의 결과. domain 타입을 밖으로 내보내지 않는다(ADR-0005). */
data class StoreResult(
    val storeId: StoreId,
    val code: String,
    val name: String,
    val address: AddressResult,
    val phone: String,
    val status: StoreStatusResult,
    /** `PREPARING`이면 개점 예정일, 개점한 뒤에는 실제 개점일 */
    val openingDate: LocalDate,
    val closingDate: LocalDate?,
    val version: Long,
) {
    companion object {
        fun from(store: Store): StoreResult =
            StoreResult(
                storeId = store.id,
                code = store.code.value,
                name = store.name,
                address =
                    AddressResult(
                        roadAddress = store.address.roadAddress,
                        detailAddress = store.address.detailAddress,
                        postalCode = store.address.postalCode,
                        sido = store.address.sido,
                        sigungu = store.address.sigungu,
                    ),
                phone = store.phone,
                status = StoreStatusResult.valueOf(store.status.name),
                openingDate = store.openingDate,
                closingDate = store.closingDate,
                version = store.version,
            )
    }
}

data class AddressResult(
    val roadAddress: String,
    val detailAddress: String?,
    val postalCode: String,
    val sido: String,
    val sigungu: String,
)
