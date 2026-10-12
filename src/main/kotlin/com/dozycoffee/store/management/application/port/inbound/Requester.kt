package com.dozycoffee.store.management.application.port.inbound

import com.dozycoffee.auth.core.PrincipalKey

/**
 * 요청한 사람. 검증된 토큰의 principal key와 역할이다. 토큰을 이 타입으로 바꾸는 일은 web 계층이 한다.
 * 역할은 Auth가 정한 문자열이라 Store가 모르는 역할도 섞여 있을 수 있다. Store는 [StoreRoles]에 있는 것만 본다.
 */
data class Requester(
    val principal: PrincipalKey,
    val roles: Set<String>,
) {
    fun hasRole(role: String): Boolean = role in roles
}
