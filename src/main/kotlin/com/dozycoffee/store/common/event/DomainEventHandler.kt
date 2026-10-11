package com.dozycoffee.store.common.event

import com.dozycoffee.store.core.DomainEvent
import kotlin.reflect.KClass

/** 다른 모듈이 내보낸 도메인 이벤트를 구독한다. 발행한 트랜잭션 안에서 동기로 실행된다. */
interface DomainEventHandler<E : DomainEvent> {
    val eventType: KClass<E>

    suspend fun handle(event: E)
}
