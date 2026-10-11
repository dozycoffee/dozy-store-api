package com.dozycoffee.store.management.application.port.inbound.command

import com.dozycoffee.store.management.domain.store.Address
import java.time.LocalDate

/** 매장 등록 요청. 개점 예정일은 등록할 때 입력한다(요구사항 1.1). 매장 코드와 상태는 시스템이 정한다. */
data class RegisterStoreCommand(
    val name: String,
    val address: Address,
    val phone: String,
    val openingDate: LocalDate,
)
