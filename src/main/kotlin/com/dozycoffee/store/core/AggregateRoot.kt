package com.dozycoffee.store.core

/** 도메인 이벤트를 쌓아 두는 애그리거트의 뿌리. application이 저장한 뒤 [pullDomainEvents]로 꺼내 처리한다. */
abstract class AggregateRoot {
    private val domainEvents = mutableListOf<DomainEvent>()

    protected fun registerEvent(event: DomainEvent) {
        domainEvents += event
    }

    fun pullDomainEvents(): List<DomainEvent> = domainEvents.toList().also { domainEvents.clear() }
}
