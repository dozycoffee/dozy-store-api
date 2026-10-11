package com.dozycoffee.store.management.domain.store.exception

import com.dozycoffee.store.core.DomainException
import com.dozycoffee.store.core.StoreId

class StoreNotFoundException(
    storeId: StoreId,
) : DomainException(StoreErrorCode.STORE_NOT_FOUND, "매장을 찾을 수 없습니다: $storeId")
