package com.dozycoffee.store.management.application.port.inbound

import com.dozycoffee.auth.core.AuthenticatedPrincipal
import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.store.common.security.StoreRoles

/**
 * 요청한 사람. 검증된 토큰의 principal key와 역할이다. 토큰을 이 타입으로 바꾸는 일은 web 계층이 한다.
 * 역할은 인증 스타터가 `store:` 접두사를 떼어 전달한 코드다([StoreRoles]). Store의 역할 중 이 판단에 쓰지 않는 것(예: `catalog_agent`)도
 * 섞여 있을 수 있으므로 [StoreRoles]에 있는 것만 본다.
 */
data class Requester(
    val principal: PrincipalKey,
    val roles: Set<String>,
) {
    fun hasRole(role: String): Boolean = role in roles

    /**
     * 매장 매니저로 인정할 수 있는 계정인가. 파트너(점주) 계정이거나 [StoreRoles.MANAGER] 역할을 가진 직원 계정이다.
     * 실제로 어느 매장의 매니저인지는 매니저 지정이 정한다(요구사항 1.3, 2장).
     */
    fun canBeStoreManager(): Boolean =
        when (principal.type) {
            PrincipalType.PARTNER -> true
            PrincipalType.EMPLOYEE -> hasRole(StoreRoles.MANAGER)
            PrincipalType.SYSTEM, PrincipalType.CUSTOMER -> false
        }

    companion object {
        /** 검증된 토큰의 요청자. 역할은 스타터가 접두사를 뗀 코드 그대로 담는다. */
        fun from(principal: AuthenticatedPrincipal): Requester = Requester(principal.key, principal.roles)
    }
}
