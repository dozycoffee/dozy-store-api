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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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

    @Nested
    @DisplayName("S5 이벤트 반영 (apply)")
    inner class Apply {
        private val first = Instant.parse("2026-10-14T01:00:00Z")
        private val later = Instant.parse("2026-10-14T05:00:00Z")

        private fun product(
            scope: StoreScope = StoreScope.All,
            sequence: Long = 5,
            sku: Sku? = Sku("DZ-00000001"),
            status: CatalogProductStatus = CatalogProductStatus.DRAFT,
            deleted: Boolean = false,
        ) = catalogProduct(sku = sku, status = status, scope = scope, sequence = sequence, deleted = deleted, lastEventAt = first)

        @Test
        fun `순번이 큰 이벤트는 상태 전체로 교체한다`() {
            val product = product()

            val result =
                product.apply(
                    snapshot(
                        sku = Sku("DZ-00000009"),
                        status = CatalogProductStatus.ACTIVE,
                        scope = StoreScope.All,
                        sequence = 6,
                        occurredAt = later,
                    ),
                )

            assertTrue(result.applied)
            assertEquals(Sku("DZ-00000009"), product.sku)
            assertEquals(CatalogProductStatus.ACTIVE, product.status)
            assertEquals(6, product.sequence)
            assertEquals(later, product.lastEventAt)
        }

        @Test
        fun `S5 2a 순번 6을 반영한 뒤 순번 5가 오면 무시하고 상태가 그대로다`() {
            val product = product(sequence = 4)
            product.apply(snapshot(status = CatalogProductStatus.ACTIVE, sequence = 6, occurredAt = later))

            val result = product.apply(snapshot(status = CatalogProductStatus.DISCONTINUED, sequence = 5))

            assertEquals(CatalogProductApplyResult.NOT_APPLIED, result)
            assertEquals(CatalogProductStatus.ACTIVE, product.status)
            assertEquals(6, product.sequence)
            assertEquals(later, product.lastEventAt)
        }

        @Test
        fun `같은 순번(중복)은 무시한다`() {
            val product = product(sequence = 5)

            val result = product.apply(snapshot(status = CatalogProductStatus.ACTIVE, sequence = 5, occurredAt = later))

            assertFalse(result.applied)
            assertEquals(CatalogProductStatus.DRAFT, product.status)
            assertEquals(first, product.lastEventAt)
        }

        @Test
        fun `반영하지 않으면 이벤트도 쌓지 않는다`() {
            val product = product(scope = StoreScope.All, sequence = 5)

            product.apply(snapshot(scope = StoreScope.Limited(setOf(StoreId(1))), sequence = 5))

            assertTrue(product.pullDomainEvents().isEmpty())
        }

        @Test
        fun `판매 범위가 바뀌면 이벤트를 쌓고 바뀜을 알린다`() {
            val limited = StoreScope.Limited(setOf(StoreId(1), StoreId(2)))
            val other = StoreScope.Limited(setOf(StoreId(3)))
            val product = product(scope = StoreScope.All, sequence = 1)

            val toLimited = product.apply(snapshot(scope = limited, sequence = 2))
            val toOther = product.apply(snapshot(scope = other, sequence = 3))
            val toAll = product.apply(snapshot(scope = StoreScope.All, sequence = 4))

            assertEquals(CatalogProductApplyResult(applied = true, scopeChanged = true), toLimited)
            assertEquals(CatalogProductApplyResult(applied = true, scopeChanged = true), toOther)
            assertEquals(CatalogProductApplyResult(applied = true, scopeChanged = true), toAll)
            assertEquals(
                listOf(
                    CatalogProductScopeChanged(ProductId(1), limited),
                    CatalogProductScopeChanged(ProductId(1), other),
                    CatalogProductScopeChanged(ProductId(1), StoreScope.All),
                ),
                product.pullDomainEvents(),
            )
        }

        @Test
        fun `판매 범위가 같으면 반영은 하지만 이벤트를 쌓지 않는다 (활성화, 같은 범위로 다시 지정)`() {
            val scope = StoreScope.Limited(setOf(StoreId(1), StoreId(2)))
            val product = product(scope = scope, sequence = 1)

            val activated = product.apply(snapshot(status = CatalogProductStatus.ACTIVE, scope = scope, sequence = 2))
            val sameAgain =
                product.apply(
                    snapshot(status = CatalogProductStatus.ACTIVE, scope = StoreScope.Limited(setOf(StoreId(2), StoreId(1))), sequence = 3),
                )

            assertEquals(CatalogProductApplyResult(applied = true, scopeChanged = false), activated)
            assertEquals(CatalogProductApplyResult(applied = true, scopeChanged = false), sameAgain)
            assertEquals(3, product.sequence)
            assertTrue(product.pullDomainEvents().isEmpty())
        }

        @Test
        fun `SKU가 생기거나 사라지는 교체를 허용한다`() {
            val product = product(sku = null, sequence = 1)

            product.apply(snapshot(sku = Sku("DZ-00000002"), sequence = 2))
            assertEquals(Sku("DZ-00000002"), product.sku)

            product.apply(snapshot(sku = null, sequence = 3))
            assertNull(product.sku)
        }

        @Test
        fun `다른 상품의 상태는 거부하고 복제본이 그대로다`() {
            val product = product(sequence = 5)

            assertFailsWith<IllegalArgumentException> { product.apply(snapshot(productId = ProductId(2), sequence = 9)) }

            assertEquals(5, product.sequence)
            assertEquals(CatalogProductStatus.DRAFT, product.status)
            assertTrue(product.pullDomainEvents().isEmpty())
        }

        @Test
        fun `삭제된 복제본은 더 큰 순번이어도 반영하지 않는다`() {
            val product = product(deleted = true, sequence = 5)

            val result =
                product.apply(
                    snapshot(status = CatalogProductStatus.ACTIVE, scope = StoreScope.Limited(setOf(StoreId(1))), sequence = 9),
                )

            assertEquals(CatalogProductApplyResult.NOT_APPLIED, result)
            assertTrue(product.deleted)
            assertEquals(CatalogProductStatus.DRAFT, product.status)
            assertEquals(5, product.sequence)
            assertTrue(product.pullDomainEvents().isEmpty())
        }
    }

    @Nested
    @DisplayName("재고 추적 여부 불변")
    inner class TracksInventoryInvariant {
        @Test
        fun `같은 값이면 정상 반영한다`() {
            val product = catalogProduct(tracksInventory = true, sequence = 1)

            val result = product.apply(snapshot(tracksInventory = true, sequence = 2))

            assertTrue(result.applied)
            assertTrue(product.tracksInventory)
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `반영할 이벤트가 값을 바꾸면 거부하고 아무것도 바뀌지 않는다`(tracks: Boolean) {
            val product = catalogProduct(tracksInventory = tracks, status = CatalogProductStatus.DRAFT, sequence = 1)

            assertFailsWith<IllegalStateException> {
                product.apply(
                    snapshot(
                        tracksInventory = !tracks,
                        status = CatalogProductStatus.ACTIVE,
                        scope = StoreScope.Limited(setOf(StoreId(1))),
                        sequence = 2,
                        occurredAt = Instant.parse("2026-10-14T05:00:00Z"),
                    ),
                )
            }

            assertEquals(tracks, product.tracksInventory)
            assertEquals(CatalogProductStatus.DRAFT, product.status)
            assertEquals(StoreScope.All, product.scope)
            assertEquals(1, product.sequence)
            assertEquals(Instant.parse("2026-10-14T01:00:00Z"), product.lastEventAt)
            assertTrue(product.pullDomainEvents().isEmpty())
        }

        @Test
        fun `순번이 크지 않은 이벤트는 값이 달라도 검사하지 않고 무시한다`() {
            val product = catalogProduct(tracksInventory = false, sequence = 5)

            val older = product.apply(snapshot(tracksInventory = true, sequence = 4))
            val same = product.apply(snapshot(tracksInventory = true, sequence = 5))

            assertEquals(CatalogProductApplyResult.NOT_APPLIED, older)
            assertEquals(CatalogProductApplyResult.NOT_APPLIED, same)
        }

        @Test
        fun `삭제된 복제본은 값이 달라도 검사하지 않고 무시한다`() {
            val product = catalogProduct(tracksInventory = false, deleted = true, sequence = 5)

            val result = product.apply(snapshot(tracksInventory = true, sequence = 9))

            assertEquals(CatalogProductApplyResult.NOT_APPLIED, result)
        }
    }
}
