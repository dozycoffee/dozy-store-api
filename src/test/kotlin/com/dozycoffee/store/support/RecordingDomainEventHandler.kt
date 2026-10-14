package com.dozycoffee.store.support

import com.dozycoffee.store.common.event.DomainEventHandler
import com.dozycoffee.store.core.DomainEvent
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * 도메인 이벤트가 같은 트랜잭션에서 처리되는지 확인하는 테스트 전용 핸들러. 받은 이벤트를 순서대로 기록한다.
 * [failOn]에 이벤트 타입을 넣으면 그 이벤트를 받을 때 예외를 던져, 반영이 롤백되는지 확인할 수 있다.
 * 테스트 클래스가 [Config]를 `@Import`해서 쓰고 `@BeforeEach`에서 [reset]한다.
 */
class RecordingDomainEventHandler : DomainEventHandler<DomainEvent> {
    override val eventType: KClass<DomainEvent> = DomainEvent::class

    val events: MutableList<DomainEvent> = CopyOnWriteArrayList()
    var failOn: KClass<out DomainEvent>? = null

    override suspend fun handle(event: DomainEvent) {
        events += event
        if (failOn?.isInstance(event) == true) error("테스트용 핸들러 실패: $event")
    }

    fun reset() {
        events.clear()
        failOn = null
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        fun recordingDomainEventHandler(): RecordingDomainEventHandler = RecordingDomainEventHandler()
    }
}

/** 이 핸들러를 쓰는 테스트 클래스에 붙인다. */
@Import(RecordingDomainEventHandler.Config::class)
annotation class WithRecordingDomainEvents
