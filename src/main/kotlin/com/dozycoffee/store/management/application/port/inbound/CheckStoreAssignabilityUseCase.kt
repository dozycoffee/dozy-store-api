package com.dozycoffee.store.management.application.port.inbound

import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.application.port.inbound.result.UnassignableStoreResult

/**
 * Catalog가 판매 범위를 바꾸거나 판매 범위 예약을 등록할 때 대상 매장을 확인한다(요구사항 7.2).
 * 개점 준비·영업 중·휴업 매장은 지정할 수 있고, 없는 매장과 폐점한 매장은 지정할 수 없다(요구사항 1.4).
 */
interface CheckStoreAssignabilityUseCase {
    /**
     * 지정할 수 없는 매장과 이유를 돌려준다. 모두 지정할 수 있으면 빈 목록이다.
     * 지정할 수 없는 매장이 있어도 오류가 아니다. 거부할지는 Catalog가 정한다.
     * 결과는 요청한 ID 순서를 따르고, 같은 ID가 여러 번 와도 한 번만 판단한다.
     * 매장의 현재 상태를 읽을 뿐 잠그지 않는다. 확인 뒤에 폐점해도 그 매장은 노출 판단 0단계가 막는다.
     */
    suspend fun check(storeIds: List<StoreId>): List<UnassignableStoreResult>

    companion object {
        /** 한 번에 확인할 수 있는 매장 수. web이 요청 오류로 먼저 거르고, 여기서는 호출하는 코드의 실수를 한 번 더 막는다. */
        const val MAX_STORE_IDS = 1_000
    }
}
