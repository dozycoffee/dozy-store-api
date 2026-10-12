package com.dozycoffee.store.management.application.port.inbound

/** Store가 쓰는 역할 이름(docs/api/README.md 역할). 역할은 Auth에 등록하고, 경로 인가 설정과 유스케이스가 같은 값을 쓴다. */
object StoreRoles {
    /** 매장 관리 권한: 모든 매장 읽기·쓰기 */
    const val ADMIN = "store:admin"

    /** 본사 조회 권한: 모든 매장 읽기만 */
    const val VIEWER = "store:viewer"

    /** 매장 매니저 역할(본사 직원): 이 역할만으로는 어떤 매장도 관리할 수 없고, 매니저로 지정된 매장만 다룬다 */
    const val MANAGER = "store:manager"
}
