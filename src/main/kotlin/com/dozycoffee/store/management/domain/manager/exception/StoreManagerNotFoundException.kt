package com.dozycoffee.store.management.domain.manager.exception

import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.store.core.DomainException
import com.dozycoffee.store.core.StoreId

class StoreManagerNotFoundException(
    storeId: StoreId,
    principal: PrincipalKey,
) : DomainException(
        StoreManagerErrorCode.STORE_MANAGER_NOT_FOUND,
        "매니저 지정을 찾을 수 없습니다: 매장 $storeId, 계정 ${principal.sub}",
    )
