package com.dozycoffee.store.management.domain.store

import com.dozycoffee.store.fixture.address
import com.dozycoffee.store.fixture.newStore
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("NewStore (등록하려는 매장)")
class NewStoreTest {
    @Test
    fun `입력한 값을 그대로 유지한다`() {
        val newStore =
            NewStore(
                code = StoreCode.create(101),
                name = "강남역점",
                address = address(),
                phone = "02-1234-5678",
                openingDate = LocalDate.of(2026, 11, 1),
            )

        assertEquals(StoreCode.create(101), newStore.code)
        assertEquals("강남역점", newStore.name)
        assertEquals(address(), newStore.address)
        assertEquals("02-1234-5678", newStore.phone)
        assertEquals(LocalDate.of(2026, 11, 1), newStore.openingDate)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t"])
    fun `매장명이 비어 있으면 거부한다`(name: String) {
        assertFailsWith<IllegalArgumentException> { newStore(name = name) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t"])
    fun `전화번호가 비어 있으면 거부한다`(phone: String) {
        assertFailsWith<IllegalArgumentException> { newStore(phone = phone) }
    }
}
