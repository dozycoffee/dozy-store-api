package com.dozycoffee.store.management.application.port.inbound

import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.application.port.inbound.result.StoreResult

/**
 * 매장의 영업 상태를 바꾼다(요구사항 1.2). 상태는 일반 정보 수정이 아니라 이 전용 전환으로만 바뀐다.
 * 즉시 반영하며 예약은 없다. 버전은 받지 않는다. 전환하면 버전이 올라 그 사이 정보 수정 화면이 충돌로 다시 보게 한다.
 *
 * 없는 매장은 `StoreNotFoundException`, 허용되지 않는 전환은 `InvalidStoreStatusTransitionException`으로 거부한다.
 */
interface ChangeStoreStatusUseCase {
    /** 개점: `PREPARING → OPEN`. 개점일이 오늘(업무 시간대)로 바뀐다. */
    suspend fun open(id: StoreId): StoreResult

    /** 휴업: `OPEN → SUSPENDED` */
    suspend fun suspend(id: StoreId): StoreResult

    /** 재개: `SUSPENDED → OPEN` */
    suspend fun resume(id: StoreId): StoreResult

    /** 폐점: `PREPARING` / `OPEN` / `SUSPENDED` → `CLOSED`. 폐점일이 오늘(업무 시간대)로 기록된다. 폐점한 매장은 다시 열 수 없다. */
    suspend fun close(id: StoreId): StoreResult
}
