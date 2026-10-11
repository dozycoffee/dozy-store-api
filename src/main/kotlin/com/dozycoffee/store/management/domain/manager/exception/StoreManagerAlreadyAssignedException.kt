package com.dozycoffee.store.management.domain.manager.exception

import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.store.core.DomainException
import com.dozycoffee.store.core.StoreId

class StoreManagerAlreadyAssignedException(
    storeId: StoreId,
    principal: PrincipalKey,
) : DomainException(
        StoreManagerErrorCode.STORE_MANAGER_ALREADY_ASSIGNED,
        "이미 그 매장의 매니저로 지정된 계정입니다: 매장 $storeId, 계정 ${principal.sub}",
    )
