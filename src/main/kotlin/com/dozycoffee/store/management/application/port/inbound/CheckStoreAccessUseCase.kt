package com.dozycoffee.store.management.application.port.inbound

import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.application.port.inbound.result.StoreAccessResult

interface CheckStoreAccessUseCase {
    /**
     * 요청자가 그 매장을 [mode]로 다룰 수 있는지 확인하고, 매장 상태와 함께 돌려준다(요구사항 2장).
     *
     * - `store:admin`은 모든 매장을 읽고 쓸 수 있고, `store:viewer`는 모든 매장을 읽을 수 있다.
     * - 그 밖에는 그 매장의 매니저 지정이 있어야 한다. 파트너 계정이거나 `store:manager` 역할을 가진 직원 계정만 매니저로 인정하고,
     *   토큰의 principal key가 지정된 쌍과 계정 종류·ID 모두 같아야 한다.
     *
     * 없는 매장과 접근할 수 없는 매장은 구분하지 않고 같은 `StoreNotFoundException`으로 거부한다.
     * 매장 ID를 바꿔 가며 다른 매장이 있는지 알아낼 수 없게 하기 위해서다.
     */
    suspend fun check(
        requester: Requester,
        storeId: StoreId,
        mode: StoreAccessMode,
    ): StoreAccessResult
}
