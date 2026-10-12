package com.dozycoffee.store.management.application.port.inbound

import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.store.management.application.port.inbound.result.StoreManagerResult

interface GetStoreManagersByPrincipalUseCase {
    /**
     * 한 계정이 매니저인 지정 목록. 폐점한 매장의 지정도 포함하고 지정한 순서다.
     * 탈퇴·퇴사한 계정의 남은 지정을 찾아 해제할 때 쓴다(요구사항 1.3).
     */
    suspend fun getByPrincipal(principal: PrincipalKey): List<StoreManagerResult>
}
