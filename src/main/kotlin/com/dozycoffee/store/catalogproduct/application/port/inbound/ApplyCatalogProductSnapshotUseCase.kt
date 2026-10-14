package com.dozycoffee.store.catalogproduct.application.port.inbound

import com.dozycoffee.store.catalogproduct.application.port.inbound.command.ApplyCatalogProductSnapshotCommand
import com.dozycoffee.store.catalogproduct.application.port.inbound.result.ApplyOutcome

interface ApplyCatalogProductSnapshotUseCase {
    /**
     * Catalog 이벤트의 상품 상태 전체를 복제본에 반영한다(요구사항 3.5, 7.1). 이벤트 종류와 관계없이 같은 방식이다.
     *
     * 복제본이 없으면 새로 만들고, 있으면 순번이 클 때만 상태 전체로 교체한다. 오래됐거나 중복된 이벤트, 삭제된 상품의 이벤트는
     * 아무것도 바꾸지 않고 [ApplyOutcome.IGNORED]다. 판매 범위가 바뀌면 같은 트랜잭션에서 구독 측(매장 설정 정리)이 처리되고,
     * 그 처리가 실패하면 반영도 롤백된다.
     *
     * 재고 추적 여부가 바뀐 이벤트와 다른 상품이 쓰는 SKU를 보낸 이벤트는 Catalog의 계약 위반이라 예외로 거부한다(롤백).
     */
    suspend fun apply(command: ApplyCatalogProductSnapshotCommand): ApplyOutcome
}
