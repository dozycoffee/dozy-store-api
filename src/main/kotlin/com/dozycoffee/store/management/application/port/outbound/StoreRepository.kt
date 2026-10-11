package com.dozycoffee.store.management.application.port.outbound

import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.domain.store.NewStore
import com.dozycoffee.store.management.domain.store.Store

/** 매장 저장소. 트랜잭션은 열지 않는다. application 서비스의 `inTransaction` 안에서 호출된다. */
interface StoreRepository {
    /**
     * 등록하려는 매장을 저장하고, ID와 버전이 채워진 [Store]를 돌려준다.
     * 폐점하지 않은 다른 매장과 이름이 같으면 [com.dozycoffee.store.management.domain.store.exception.StoreNameDuplicatedException]으로 거부한다.
     */
    suspend fun insert(store: NewStore): Store

    suspend fun findById(id: StoreId): Store?

    /** 행을 잠그고(`SELECT … FOR UPDATE`) 조회한다. 상태 전환처럼 읽고 바꾸는 사이에 다른 변경이 끼면 안 될 때 쓴다. */
    suspend fun findByIdForUpdate(id: StoreId): Store?

    /**
     * 바뀐 매장을 저장하고, 버전이 1 오른 [Store]를 돌려준다. 낙관적 잠금이라 [Store.version]이 저장소의 버전과 같아야 한다.
     * 그 사이 다른 변경이 반영됐으면 [com.dozycoffee.store.core.VersionConflictException]으로 거부한다.
     * 이름을 바꿔 폐점하지 않은 다른 매장과 겹치면 [com.dozycoffee.store.management.domain.store.exception.StoreNameDuplicatedException]으로 거부한다.
     */
    suspend fun save(store: Store): Store

    /**
     * 폐점하지 않은 매장 중에 같은 이름이 있는지. [excluding]을 주면 그 매장은 빼고 본다
     * (이름을 그대로 두고 다른 정보만 고치는 수정이 자기 자신 때문에 중복으로 보이지 않게).
     */
    suspend fun existsActiveByName(
        name: String,
        excluding: StoreId? = null,
    ): Boolean
}
