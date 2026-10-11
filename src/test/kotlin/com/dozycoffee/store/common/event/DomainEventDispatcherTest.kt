package com.dozycoffee.store.common.event

import com.dozycoffee.store.core.DomainEvent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("DomainEventDispatcher")
class DomainEventDispatcherTest {
    private data class A(
        val n: Int,
    ) : DomainEvent

    private data class B(
        val n: Int,
    ) : DomainEvent

    private class Recorder<E : DomainEvent>(
        override val eventType: KClass<E>,
        private val log: MutableList<String>,
        private val fail: Boolean = false,
    ) : DomainEventHandler<E> {
        override suspend fun handle(event: E) {
            log += "${eventType.simpleName}:$event"
            if (fail) error("핸들러 실패")
        }
    }

    @Test
    fun `이벤트 타입에 맞는 핸들러에만 발생 순서대로 넘긴다`() =
        runTest {
            val log = mutableListOf<String>()
            val dispatcher = DomainEventDispatcher(listOf(Recorder(A::class, log), Recorder(B::class, log)))

            dispatcher.dispatch(listOf(A(1), B(2), A(3)))

            assertEquals(listOf("A:A(n=1)", "B:B(n=2)", "A:A(n=3)"), log)
        }

    @Test
    fun `구독자가 없으면 아무 일도 없다`() =
        runTest {
            DomainEventDispatcher(emptyList()).dispatch(listOf(A(1)))
        }

    @Test
    fun `핸들러가 실패하면 예외를 그대로 던져 호출한 트랜잭션이 롤백되게 한다`() =
        runTest {
            val log = mutableListOf<String>()
            val dispatcher = DomainEventDispatcher(listOf(Recorder(A::class, log, fail = true), Recorder(B::class, log)))

            assertFailsWith<IllegalStateException> { dispatcher.dispatch(listOf(A(1), B(2))) }
            assertEquals(listOf("A:A(n=1)"), log)
        }
}
