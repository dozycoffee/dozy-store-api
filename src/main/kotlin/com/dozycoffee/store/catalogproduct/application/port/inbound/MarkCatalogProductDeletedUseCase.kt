package com.dozycoffee.store.catalogproduct.application.port.inbound

import com.dozycoffee.store.catalogproduct.application.port.inbound.command.MarkCatalogProductDeletedCommand
import com.dozycoffee.store.catalogproduct.application.port.inbound.result.DeleteOutcome

interface MarkCatalogProductDeletedUseCase {
    /**
     * 상품 복제본을 삭제로 표시한다(요구사항 3.5). 행을 지우지 않는 것은 늦게 도착한 그 상품의 오래된 이벤트로 상품이
     * 되살아나지 않게 하기 위해서다. 표시한 뒤 같은 트랜잭션에서 구독 측(그 상품의 매장 데이터 삭제)이 처리되고,
     * 그 처리가 실패하면 삭제 표시도 롤백된다.
     *
     * 이미 삭제된 복제본이면 [DeleteOutcome.ALREADY_DELETED], 복제본이 없으면 [DeleteOutcome.UNKNOWN_PRODUCT]로 아무것도 바꾸지 않는다.
     */
    suspend fun markDeleted(command: MarkCatalogProductDeletedCommand): DeleteOutcome
}
