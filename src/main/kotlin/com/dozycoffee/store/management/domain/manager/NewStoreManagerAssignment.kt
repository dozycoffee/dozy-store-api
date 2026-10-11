package com.dozycoffee.store.management.domain.manager

import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.domain.manager.exception.InvalidManagerPrincipalTypeException

/**
 * 아직 저장하지 않은, 지정하려는 매장 매니저. 지정한 시각은 저장할 때 DB 시계로 정해지므로 갖지 않는다. 저장하면 [StoreManagerAssignment]가 된다.
 *
 * 계정은 Auth의 principal key(계정 종류와 계정 ID의 쌍)로 가리킨다. Store는 계정이 실제로 있는지 확인하지 않고 요청의 값을 믿는다(요구사항 1.3).
 * 매니저로 지정할 수 있는 계정 종류는 파트너(점주)와 본사 직원뿐이다. 그 밖의 종류는 [InvalidManagerPrincipalTypeException]으로 거부한다.
 */
class NewStoreManagerAssignment(
    val storeId: StoreId,
    val principal: PrincipalKey,
) {
    init {
        if (principal.type !in ASSIGNABLE_PRINCIPAL_TYPES) throw InvalidManagerPrincipalTypeException(principal.type)
    }
}

/** 매니저로 지정할 수 있는 계정 종류. ERD의 `store_managers.principal_type` CHECK와 같다. */
internal val ASSIGNABLE_PRINCIPAL_TYPES: Set<PrincipalType> = setOf(PrincipalType.PARTNER, PrincipalType.EMPLOYEE)
