package com.dozycoffee.store.catalogproduct.application.port.inbound.command

import com.dozycoffee.store.core.ProductId
import java.time.Instant

/**
 * Catalog가 상품을 삭제했다고 알려 왔다(요구사항 7.1). 메시지 컨슈머가 삭제 이벤트를 이 타입으로 번역해 넘긴다.
 * 삭제 이벤트의 순번은 쓰지 않는다. 삭제는 종료 상태라 어떤 이벤트도 이후에 반영되지 않는다.
 *
 * @property occurredAt 이벤트 발생 시각. 기록용이다.
 */
data class MarkCatalogProductDeletedCommand(
    val productId: ProductId,
    val occurredAt: Instant,
)
