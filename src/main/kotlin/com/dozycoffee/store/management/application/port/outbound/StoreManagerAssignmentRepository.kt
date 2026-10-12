package com.dozycoffee.store.management.application.port.outbound

import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.domain.manager.NewStoreManagerAssignment
import com.dozycoffee.store.management.domain.manager.StoreManagerAssignment

/** 매장 매니저 지정 저장소. 트랜잭션은 열지 않는다. application 서비스의 `inTransaction` 안에서 호출된다. */
interface StoreManagerAssignmentRepository {
    /**
     * 지정을 저장하고, 지정 시각(DB 시계)이 채워진 [StoreManagerAssignment]를 돌려준다.
     * 같은 (매장, 계정 종류, 계정 ID)가 이미 있으면
     * [com.dozycoffee.store.management.domain.manager.exception.StoreManagerAlreadyAssignedException]으로 거부한다.
     */
    suspend fun insert(assignment: NewStoreManagerAssignment): StoreManagerAssignment

    /** 그 (매장, 계정)의 지정이 있는지. 계정 종류와 ID가 모두 같아야 한다. */
    suspend fun exists(
        storeId: StoreId,
        principal: PrincipalKey,
    ): Boolean

    /** 지정을 지운다. 지울 대상이 있었으면 `true`, 없었으면 `false`다(없을 때의 거부는 서비스가 정한다). */
    suspend fun delete(
        storeId: StoreId,
        principal: PrincipalKey,
    ): Boolean

    /** 매장의 매니저 지정. 지정한 순서다. */
    suspend fun findByStoreId(storeId: StoreId): List<StoreManagerAssignment>

    /** 한 계정이 매니저인 매장들의 지정(폐점한 매장 포함). 지정한 순서다. */
    suspend fun findByPrincipal(principal: PrincipalKey): List<StoreManagerAssignment>
}
