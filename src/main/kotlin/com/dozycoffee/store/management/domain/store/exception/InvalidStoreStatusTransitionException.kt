package com.dozycoffee.store.management.domain.store.exception

import com.dozycoffee.store.core.DomainException
import com.dozycoffee.store.management.domain.store.StoreStatus

class InvalidStoreStatusTransitionException(
    from: StoreStatus,
    to: StoreStatus,
) : DomainException(StoreErrorCode.INVALID_STORE_STATUS_TRANSITION, "허용되지 않는 매장 상태 전환입니다: $from → $to")
