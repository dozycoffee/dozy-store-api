package com.dozycoffee.store.management.domain.store.exception

import com.dozycoffee.store.core.DomainException
import com.dozycoffee.store.management.domain.store.StoreStatus

class OpeningDateNotChangeableException(
    status: StoreStatus,
) : DomainException(
        StoreErrorCode.OPENING_DATE_NOT_CHANGEABLE,
        "개점 예정일은 개점 준비 중인 매장만 바꿀 수 있습니다: 현재 $status",
    )
