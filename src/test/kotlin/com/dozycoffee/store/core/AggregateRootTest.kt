package com.dozycoffee.store.core

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("AggregateRoot")
class AggregateRootTest {
    private data class Happened(
        val n: Int,
    ) : DomainEvent

    private class Sample : AggregateRoot() {
        fun act(n: Int) = registerEvent(Happened(n))
    }

    @Test
    fun `쌓인 이벤트를 발생 순서대로 꺼내면 비워진다`() {
        val sample = Sample().apply { act(1) }
        sample.act(2)

        assertEquals(listOf(Happened(1), Happened(2)), sample.pullDomainEvents())
        assertTrue(sample.pullDomainEvents().isEmpty())
    }
}
