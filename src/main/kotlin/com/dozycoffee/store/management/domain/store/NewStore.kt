package com.dozycoffee.store.management.domain.store

import java.time.LocalDate

/**
 * 아직 저장하지 않은, 등록하려는 매장. 매장 ID와 버전은 저장할 때 DB가 정하므로 갖지 않는다.
 * 등록한 매장은 항상 `PREPARING` 상태이고 폐점일이 없다(요구사항 1.2). 저장하면 [Store]가 된다.
 *
 * 매장 코드는 등록 시점에 시스템이 부여하므로 호출하는 쪽(application)이 발급해 넘긴다. [openingDate]는 개점 예정일이다.
 */
class NewStore(
    val code: StoreCode,
    val name: String,
    val address: Address,
    val phone: String,
    val openingDate: LocalDate,
) {
    init {
        requireStoreName(name)
        requireStorePhone(phone)
    }
}
