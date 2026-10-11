package com.dozycoffee.store.management.application.port.inbound.command

import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.domain.store.Address
import java.time.LocalDate

/**
 * 매장 정보 수정 요청. 등록과 같은 값으로 매장 정보 전체를 바꾼다(요구사항 1.1).
 * [expectedVersion]은 요청한 사람이 화면에서 본 버전이다. 그 사이 다른 변경이 반영됐으면 거부한다(낙관적 잠금).
 * 매장 코드와 상태는 이 요청으로 바꾸지 않는다.
 */
data class UpdateStoreCommand(
    val id: StoreId,
    val expectedVersion: Long,
    val name: String,
    val address: Address,
    val phone: String,
    val openingDate: LocalDate,
)
