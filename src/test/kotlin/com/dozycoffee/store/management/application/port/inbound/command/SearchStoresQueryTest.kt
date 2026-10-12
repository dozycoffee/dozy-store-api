package com.dozycoffee.store.management.application.port.inbound.command

import com.dozycoffee.store.core.StoreId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("SearchStoresQuery")
class SearchStoresQueryTest {
    @Test
    fun `조건을 주지 않으면 아무것도 거르지 않는다`() {
        val query = SearchStoresQuery()

        assertTrue(query.ids.isEmpty())
        assertTrue(query.statuses.isEmpty())
        assertEquals(0, query.page.page)
        assertEquals(20, query.page.size)
    }

    @Test
    fun `ID는 최대 100개까지 받는다`() {
        assertEquals(100, SearchStoresQuery(ids = (1L..100L).map { StoreId(it) }.toSet()).ids.size)
    }

    @Test
    fun `ID가 100개를 넘으면 거부한다`() {
        assertFailsWith<IllegalArgumentException> { SearchStoresQuery(ids = (1L..101L).map { StoreId(it) }.toSet()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t"])
    fun `값이 있는 문자열 조건은 공백뿐일 수 없다`(value: String) {
        assertFailsWith<IllegalArgumentException> { SearchStoresQuery(sido = value) }
        assertFailsWith<IllegalArgumentException> { SearchStoresQuery(sigungu = value) }
        assertFailsWith<IllegalArgumentException> { SearchStoresQuery(nameContains = value) }
    }
}
