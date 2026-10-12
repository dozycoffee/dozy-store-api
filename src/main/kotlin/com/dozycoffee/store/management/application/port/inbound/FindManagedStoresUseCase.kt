package com.dozycoffee.store.management.application.port.inbound

import com.dozycoffee.store.management.application.port.inbound.result.ManagedStoreResult

interface FindManagedStoresUseCase {
    /**
     * 요청자가 매니저로 지정된 매장 목록(요구사항 1.3, 1.4). 폐점한 매장도 포함한다(읽기 전용으로 볼 수 있다).
     * 폐점하지 않은 매장이 앞에, 그 안에서 매장명 가나다순이다.
     *
     * 매니저로 인정되지 않는 요청자(매장 매니저 역할이 없는 직원 등)는 빈 목록이다.
     * `store:admin`·`store:viewer`도 모든 매장이 아니라 자기가 매니저로 지정된 매장만 돌려준다. 본사의 전체 매장 목록은 별도 조회가 맡는다.
     */
    suspend fun find(requester: Requester): List<ManagedStoreResult>
}
