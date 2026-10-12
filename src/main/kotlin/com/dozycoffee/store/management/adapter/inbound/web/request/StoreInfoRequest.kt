package com.dozycoffee.store.management.adapter.inbound.web.request

import com.dozycoffee.store.common.web.RequestValues
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.application.port.inbound.command.RegisterStoreCommand
import com.dozycoffee.store.management.application.port.inbound.command.UpdateStoreCommand
import com.dozycoffee.store.management.domain.store.Address
import com.dozycoffee.store.management.domain.store.StoreInputLimits
import java.time.LocalDate

/**
 * 매장 정보 요청 본문(docs/api/store.md). 등록(`POST`)과 수정(`PUT`)이 같은 본문과 같은 검증을 쓴다.
 * 값을 모두 널 허용으로 받아 [toInfo]가 필드 이름과 함께 검증한다.
 */
data class StoreInfoRequest(
    val name: String?,
    val address: AddressRequest?,
    val phone: String?,
    val openingDate: LocalDate?,
) {
    /** 검증하고 앞뒤 공백을 정리한 값. 위반하면 필드 이름과 함께 `InvalidRequestException`이다. */
    fun toInfo(): StoreInfo =
        StoreInfo(
            name = RequestValues.text("name", name, StoreInputLimits.NAME_MAX),
            address = RequestValues.required("address", address).toDomain("address"),
            phone = RequestValues.text("phone", phone, StoreInputLimits.PHONE_MAX),
            openingDate = RequestValues.required("openingDate", openingDate),
        )

    fun toRegisterCommand(): RegisterStoreCommand = toInfo().let { RegisterStoreCommand(it.name, it.address, it.phone, it.openingDate) }

    /** [expectedVersion]은 요청한 사람이 본 버전(`If-Match`)이다. */
    fun toUpdateCommand(
        id: StoreId,
        expectedVersion: Long,
    ): UpdateStoreCommand = toInfo().let { UpdateStoreCommand(id, expectedVersion, it.name, it.address, it.phone, it.openingDate) }
}

/** 검증을 마친 매장 정보. */
data class StoreInfo(
    val name: String,
    val address: Address,
    val phone: String,
    val openingDate: LocalDate,
)
