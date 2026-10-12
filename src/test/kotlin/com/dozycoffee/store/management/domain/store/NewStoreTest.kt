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

    @Test
    fun `매장명은 50자까지 허용하고 넘으면 거부한다`() {
        assertEquals(50, newStore(name = "가".repeat(50)).name.length)
        assertFailsWith<IllegalArgumentException> { newStore(name = "가".repeat(51)) }
    }

    @Test
    fun `전화번호는 20자까지 허용하고 넘으면 거부한다`() {
        assertEquals(20, newStore(phone = "1".repeat(20)).phone.length)
        assertFailsWith<IllegalArgumentException> { newStore(phone = "1".repeat(21)) }
    }

    @ParameterizedTest
    @ValueSource(strings = [" 강남역점", "강남역점 ", "\t강남역점", "강남역점\n", " 강남역점 "])
    fun `매장명의 앞뒤에 공백이 있으면 거부한다`(name: String) {
        assertFailsWith<IllegalArgumentException> { newStore(name = name) }
    }

    @Test
    fun `매장명 안쪽의 공백은 허용한다`() {
        assertEquals("강남 역점", newStore(name = "강남 역점").name)
    }

    @Test
    fun `전화번호는 형식을 검증하지 않는다`() {
        assertEquals("+82-2-1234-5678", newStore(phone = "+82-2-1234-5678").phone)
        assertEquals("1588-1234", newStore(phone = "1588-1234").phone)
    }
}
