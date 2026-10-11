package com.dozycoffee.store.management.domain.store.exception

import com.dozycoffee.store.core.DomainException
import com.dozycoffee.store.core.StoreId

class StoreClosedException(
    storeId: StoreId,
) : DomainException(StoreErrorCode.STORE_CLOSED, "폐점한 매장은 변경할 수 없습니다: 매장 $storeId")
