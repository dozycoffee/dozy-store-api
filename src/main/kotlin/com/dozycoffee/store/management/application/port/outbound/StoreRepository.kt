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

    /** 폐점하지 않은 매장 중에 같은 이름이 있는지. */
    suspend fun existsActiveByName(name: String): Boolean
}
