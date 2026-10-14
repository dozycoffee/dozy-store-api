package com.dozycoffee.store.catalogproduct.domain

import com.dozycoffee.store.catalogproduct.domain.event.CatalogProductScopeChanged
import com.dozycoffee.store.core.ProductId
import com.dozycoffee.store.core.Sku
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.fixture.catalogProduct
import com.dozycoffee.store.fixture.snapshot
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("CatalogProduct")
class CatalogProductTest {
    @Nested
    @DisplayName("S5 2c 처음 복제")
    inner class Replicate {
        @Test
        fun `스냅샷의 값을 그대로 담고 삭제 표시는 없다`() {
            val occurredAt = Instant.parse("2026-10-14T02:30:00Z")
            val product =
                CatalogProduct.replicate(
                    snapshot(
                        productId = ProductId(7),
                        sku = Sku("DZ-00000007"),
                        status = CatalogProductStatus.DRAFT,
                        scope = StoreScope.Limited(setOf(StoreId(1), StoreId(2))),
                        tracksInventory = true,
                        sequence = 3,
                        occurredAt = occurredAt,
                    ),
                )

            assertEquals(ProductId(7), product.productId)
            assertEquals(Sku("DZ-00000007"), product.sku)
            assertEquals(CatalogProductStatus.DRAFT, product.status)
            assertEquals(StoreScope.Limited(setOf(StoreId(1), StoreId(2))), product.scope)
            assertTrue(product.tracksInventory)
            assertEquals(3, product.sequence)
            assertFalse(product.deleted)
            assertEquals(occurredAt, product.lastEventAt)
        }

        @Test
        fun `SKU가 없는 상품도 복제한다`() {
            assertNull(CatalogProduct.replicate(snapshot(sku = null)).sku)
        }

        @Test
        fun `순번 0도 받는다`() {
            assertEquals(0, CatalogProduct.replicate(snapshot(sequence = 0)).sequence)
        }

        @Test
        fun `음수 순번은 거부한다`() {
            assertFailsWith<IllegalArgumentException> { CatalogProduct.replicate(snapshot(sequence = -1)) }
        }
    }

    @Nested
    @DisplayName("도메인 이벤트")
    inner class Events {
        @Test
        fun `처음 복제하면 판매 범위 바뀜 이벤트를 한 번 쌓는다`() {
            val scope = StoreScope.Limited(setOf(StoreId(3)))
            val product = CatalogProduct.replicate(snapshot(productId = ProductId(9), scope = scope))

            assertEquals(listOf(CatalogProductScopeChanged(ProductId(9), scope)), product.pullDomainEvents())
        }

        @Test
        fun `꺼낸 뒤에는 비어 있다`() {
            val product = CatalogProduct.replicate(snapshot())
            product.pullDomainEvents()

            assertTrue(product.pullDomainEvents().isEmpty())
        }

        @Test
        fun `모든 매장 범위로 처음 복제해도 이벤트를 쌓는다`() {
            val product = CatalogProduct.replicate(snapshot(scope = StoreScope.All))

            assertEquals(listOf(CatalogProductScopeChanged(ProductId(1), StoreScope.All)), product.pullDomainEvents())
        }
    }

    @Nested
    @DisplayName("저장된 복제본 복원")
    inner class Reconstitute {
        @Test
        fun `저장된 값을 그대로 복원하고 이벤트를 쌓지 않는다`() {
            val lastEventAt = Instant.parse("2026-10-15T00:00:00Z")
            val product =
                catalogProduct(
                    productId = ProductId(5),
                    sku = null,
                    status = CatalogProductStatus.DISCONTINUED,
                    scope = StoreScope.Limited(emptySet()),
                    tracksInventory = true,
                    sequence = 12,
                    lastEventAt = lastEventAt,
                )

            assertEquals(ProductId(5), product.productId)
            assertNull(product.sku)
            assertEquals(CatalogProductStatus.DISCONTINUED, product.status)
            assertEquals(StoreScope.Limited(emptySet()), product.scope)
            assertTrue(product.tracksInventory)
            assertEquals(12, product.sequence)
            assertEquals(lastEventAt, product.lastEventAt)
            assertTrue(product.pullDomainEvents().isEmpty())
        }

        @Test
        fun `삭제 표시된 복제본도 복원한다`() {
            assertTrue(catalogProduct(deleted = true).deleted)
        }

        @Test
        fun `음수 순번은 복원할 때도 거부한다`() {
            assertFailsWith<IllegalArgumentException> { catalogProduct(sequence = -1) }
        }
    }
}
