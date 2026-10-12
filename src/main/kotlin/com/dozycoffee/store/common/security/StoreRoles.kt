package com.dozycoffee.store.common.security

/**
 * Store가 쓰는 역할(docs/api/README.md 역할). 역할은 Auth에 `store:admin`처럼 등록한다.
 * 인증 스타터가 토큰에서 `store:` 역할만 남기고 접두사를 떼어 전달하므로, Store 안에서는 아래 코드로 다룬다.
 * `Requester`의 역할 비교와 컨트롤러의 `@PreAuthorize`(`hasRole('admin')`)가 같은 상수를 쓴다.
 */
object StoreRoles {
    /** 매장 관리 권한(`store:admin`): 모든 매장 읽기·쓰기 */
    const val ADMIN = "admin"

    /** 본사 조회 권한(`store:viewer`): 모든 매장 읽기만 */
    const val VIEWER = "viewer"

    /** 매장 매니저 역할(`store:manager`, 본사 직원): 이 역할만으로는 어떤 매장도 관리할 수 없고, 매니저로 지정된 매장만 다룬다 */
    const val MANAGER = "manager"
}
