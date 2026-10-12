package com.dozycoffee.store.management.adapter.inbound.web.request

import com.dozycoffee.store.common.web.RequestValues
import com.dozycoffee.store.management.application.port.inbound.command.RegisterStoreCommand
import com.dozycoffee.store.management.domain.store.StoreInputLimits
import java.time.LocalDate

/** 매장 등록 요청(docs/api/store.md). 값 검증은 [toCommand]가 한다. */
data class RegisterStoreRequest(
    val name: String?,
    val address: AddressRequest?,
    val phone: String?,
    val openingDate: LocalDate?,
) {
    fun toCommand(): RegisterStoreCommand =
        RegisterStoreCommand(
            name = RequestValues.text("name", name, StoreInputLimits.NAME_MAX),
            address = RequestValues.required("address", address).toDomain("address"),
            phone = RequestValues.text("phone", phone, StoreInputLimits.PHONE_MAX),
            openingDate = RequestValues.required("openingDate", openingDate),
        )
}
