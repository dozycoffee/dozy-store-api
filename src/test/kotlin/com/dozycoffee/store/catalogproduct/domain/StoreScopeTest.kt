package com.dozycoffee.store.catalogproduct.domain

import com.dozycoffee.store.core.StoreId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@DisplayName("StoreScope")
class StoreScopeTest {
    private fun limited(vararg ids: Long) = StoreScope.Limited(ids.map { StoreId(it) }.toSet())

    @Nested
    @DisplayName("All")
    inner class All {
        @Test
        fun `어떤 매장이든 포함한다`() {
            assertTrue(StoreScope.All.covers(StoreId(1)))
            assertTrue(StoreScope.All.covers(StoreId(999_999)))
        }
    }

    @Nested
    @DisplayName("Limited")
    inner class Limited {
        @Test
        fun `대상 목록에 든 매장만 포함한다`() {
            val scope = limited(1, 2)

            assertTrue(scope.covers(StoreId(1)))
            assertTrue(scope.covers(StoreId(2)))
            assertFalse(scope.covers(StoreId(3)))
        }

        @Test
        fun `대상이 비어 있으면 어떤 매장도 포함하지 않는다`() {
            val scope = limited()

            assertFalse(scope.covers(StoreId(1)))
        }

        @Test
        fun `같은 매장이 여러 번 와도 하나로 본다`() {
            val scope = StoreScope.Limited(listOf(StoreId(1), StoreId(1), StoreId(2)).toSet())

            assertEquals(setOf(StoreId(1), StoreId(2)), scope.targetStoreIds)
        }

        @Test
        fun `대상이 같으면 순서와 관계없이 같은 범위다`() {
            assertEquals(limited(1, 2), limited(2, 1))
            assertNotEquals(limited(1, 2), limited(1, 3))
        }

        @Test
        fun `전체 범위와 대상이 있는 범위는 다르다`() {
            assertNotEquals<StoreScope>(StoreScope.All, limited(1))
            assertNotEquals<StoreScope>(StoreScope.All, limited())
        }
    }
}
