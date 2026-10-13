package com.dozycoffee.store.management.adapter.inbound.web.response

import com.dozycoffee.store.management.application.port.inbound.result.StoreManagerResult
import java.time.Instant

/**
 * 매장 매니저 지정 응답(docs/api/manager.md `StoreManager`). Store는 principal key만 돌려주고 계정의 이름 같은 정보는 갖지 않는다.
 * 계정 종류와 ID는 Auth의 표현 그대로 소문자다.
 */
data class StoreManagerResponse(
    val storeId: Long,
    val principalType: String,
    val principalId: String,
    val assignedAt: Instant,
) {
    companion object {
        fun from(result: StoreManagerResult): StoreManagerResponse =
            StoreManagerResponse(
                storeId = result.storeId.value,
                principalType = result.principal.type.claimValue,
                principalId = result.principal.id.toString(),
                assignedAt = result.assignedAt,
            )
    }
}
