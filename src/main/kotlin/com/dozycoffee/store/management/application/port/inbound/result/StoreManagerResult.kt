package com.dozycoffee.store.management.application.port.inbound.result

import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.domain.manager.StoreManagerAssignment
import java.time.Instant

/** 매장 매니저 지정. 계정은 Auth와 맺은 공용 타입인 principal key 그대로 내보낸다. 이름 같은 계정 정보는 Store가 갖지 않는다(요구사항 1.3). */
data class StoreManagerResult(
    val storeId: StoreId,
    val principal: PrincipalKey,
    val assignedAt: Instant,
) {
    companion object {
        fun from(assignment: StoreManagerAssignment): StoreManagerResult =
            StoreManagerResult(assignment.storeId, assignment.principal, assignment.assignedAt)
    }
}
