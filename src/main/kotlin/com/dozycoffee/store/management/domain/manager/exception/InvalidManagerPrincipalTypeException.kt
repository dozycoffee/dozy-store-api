package com.dozycoffee.store.management.domain.manager.exception

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.store.core.DomainException

class InvalidManagerPrincipalTypeException(
    type: PrincipalType,
) : DomainException(
        StoreManagerErrorCode.INVALID_MANAGER_PRINCIPAL_TYPE,
        "매니저로 지정할 수 없는 계정 종류입니다: ${type.claimValue}",
    )
