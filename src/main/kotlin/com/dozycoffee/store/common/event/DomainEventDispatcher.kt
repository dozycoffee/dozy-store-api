package com.dozycoffee.store.common.event

import com.dozycoffee.store.core.DomainEvent
import org.springframework.stereotype.Component

/**
 * 저장 뒤 꺼낸 도메인 이벤트를 구독 핸들러에 넘긴다.
 * 호출한 코루틴에서 그대로 실행하므로 같은 트랜잭션이고, 핸들러가 실패하면 원래 변경도 롤백된다.
 * 발행 측은 구독 측을 모른다.
 */
@Component
class DomainEventDispatcher(
    handlers: List<DomainEventHandler<*>>,
) {
    private val handlers: List<DomainEventHandler<*>> = handlers.toList()

    suspend fun dispatch(events: List<DomainEvent>) {
        for (event in events) {
            for (handler in handlers) {
                if (handler.eventType.isInstance(event)) {
                    @Suppress("UNCHECKED_CAST")
                    (handler as DomainEventHandler<DomainEvent>).handle(event)
                }
            }
        }
    }
}
