package com.dozycoffee.store.management.domain.manager

import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.store.core.StoreId
import java.time.Instant

/**
 * 저장된 매장 매니저 지정. (매장, principal key) 한 쌍이 곧 식별자이고, 같은 쌍은 두 번 지정할 수 없다(요구사항 1.3).
 * 저장 전의 지정하려는 매니저는 [NewStoreManagerAssignment]다. 이 클래스는 저장소에서 복원한 [reconstitute]로만 만든다.
 *
 * 값이 바뀌지 않는 연결이라 변경 메서드가 없다. 교체는 해제와 새 지정으로 한다.
 */
class StoreManagerAssignment private constructor(
    val storeId: StoreId,
    val principal: PrincipalKey,
    val assignedAt: Instant,
) {
    init {
        check(principal.type in ASSIGNABLE_PRINCIPAL_TYPES) {
            "매니저로 지정할 수 없는 계정 종류가 저장되어 있습니다: 매장 $storeId, 계정 ${principal.sub}"
        }
    }

    companion object {
        /** 저장소에서 읽은 값으로 복원한다. 새로 지정하는 매니저는 [NewStoreManagerAssignment]를 쓴다. */
        fun reconstitute(
            storeId: StoreId,
            principal: PrincipalKey,
            assignedAt: Instant,
        ): StoreManagerAssignment = StoreManagerAssignment(storeId, principal, assignedAt)
    }
}
