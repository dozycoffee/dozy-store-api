package com.dozycoffee.store.management.domain.store.exception

import com.dozycoffee.store.core.DomainException

class StoreNameDuplicatedException(
    name: String,
) : DomainException(StoreErrorCode.STORE_NAME_DUPLICATED, "폐점하지 않은 다른 매장이 같은 이름을 쓰고 있습니다: $name")
