package com.dozycoffee.store.management.application.port.inbound

import com.dozycoffee.store.common.paging.PageResult
import com.dozycoffee.store.management.application.port.inbound.command.SearchStoresQuery
import com.dozycoffee.store.management.application.port.inbound.result.StoreResult

interface SearchStoresUseCase {
    /**
     * 매장을 조건으로 검색한다(요구사항 1.4). 매장명 가나다순이고, 같은 이름이면 ID순이다.
     * 폐점한 매장은 상태로 `CLOSED`를 지정해야 나온다. 조건에 맞는 매장이 없으면 빈 페이지다.
     */
    suspend fun search(query: SearchStoresQuery): PageResult<StoreResult>
}
