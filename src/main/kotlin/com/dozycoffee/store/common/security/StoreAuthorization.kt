package com.dozycoffee.store.common.security

/**
 * 컨트롤러의 `@PreAuthorize`에 쓰는 인가 표현(docs/architecture/integration.md 인증). 역할은 접두사를 뗀 코드로 판단한다.
 * 여러 realm을 받는 서비스라 모든 API에 계정 종류(type) 조건을 붙인다. `@dozyAuth.isType`는 WebFlux에서 `and`로 묶을 수 없어
 * `authentication` 표현을 쓴다.
 */
object StoreAuthorization {
    /** 본사 직원 계정(realm `internal`) */
    const val EMPLOYEE = "authentication.principal.key.type.name() == 'EMPLOYEE'"

    /** 매장 관리 권한을 가진 본사 직원: 모든 매장 읽기·쓰기 */
    const val ADMIN = "hasRole('${StoreRoles.ADMIN}') and $EMPLOYEE"

    /** 매장 관리 또는 본사 조회 권한을 가진 본사 직원: 모든 매장 읽기 */
    const val ADMIN_OR_VIEWER = "hasAnyRole('${StoreRoles.ADMIN}', '${StoreRoles.VIEWER}') and $EMPLOYEE"
}
