package com.dozycoffee.store.catalogproduct.adapter.outbound.persistence

import com.dozycoffee.store.catalogproduct.application.port.outbound.CatalogProductRepository
import com.dozycoffee.store.catalogproduct.application.port.outbound.SellableProductSummary
import com.dozycoffee.store.catalogproduct.domain.CatalogProduct
import com.dozycoffee.store.catalogproduct.domain.CatalogProductStatus
import com.dozycoffee.store.catalogproduct.domain.StoreScope
import com.dozycoffee.store.core.ProductId
import com.dozycoffee.store.core.Sku
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.fixture.catalogProduct
import com.dozycoffee.store.support.ApplicationTest
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("ExposedCatalogProductRepository")
class ExposedCatalogProductRepositoryTest : ApplicationTest() {
    @Autowired
    private lateinit var repository: CatalogProductRepository

    private fun stores(vararg ids: Long) = StoreScope.Limited(ids.map { StoreId(it) }.toSet())

    private fun insert(product: CatalogProduct) = tx { repository.insertIfAbsent(product) }

    private fun find(id: Long = 1) = tx { repository.findByIdForUpdate(ProductId(id)) }

    private fun targetStoreIds(id: Long = 1): Set<Long> =
        runBlocking {
            databaseClient
                .sql("SELECT store_id FROM catalog_product_target_stores WHERE product_id = $id")
                .map { row -> row.get(0, java.lang.Long::class.java)!!.toLong() }
                .all()
                .collectList()
                .awaitSingle()
                .toSet()
        }

    private fun timestamps(id: Long = 1): Pair<OffsetDateTime, OffsetDateTime> =
        runBlocking {
            databaseClient
                .sql("SELECT created_at, updated_at FROM catalog_products WHERE product_id = $id")
                .map { row -> row.get(0, OffsetDateTime::class.java)!! to row.get(1, OffsetDateTime::class.java)!! }
                .one()
                .awaitSingle()
        }

    private fun assertSame(
        expected: CatalogProduct,
        actual: CatalogProduct?,
    ) {
        assertNotNull(actual)
        assertEquals(expected.productId, actual.productId)
        assertEquals(expected.sku, actual.sku)
        assertEquals(expected.status, actual.status)
        assertEquals(expected.scope, actual.scope)
        assertEquals(expected.tracksInventory, actual.tracksInventory)
        assertEquals(expected.sequence, actual.sequence)
        assertEquals(expected.deleted, actual.deleted)
        assertEquals(expected.lastEventAt, actual.lastEventAt)
    }

    @Nested
    @DisplayName("저장 후 조회")
    inner class RoundTrip {
        @Test
        fun `모든 매장 범위의 복제본을 그대로 읽는다`() {
            val product = catalogProduct(scope = StoreScope.All, lastEventAt = Instant.parse("2026-10-14T01:23:45Z"))
            assertTrue(insert(product))

            assertSame(product, find())
        }

        @Test
        fun `대상 매장이 있는 범위를 그대로 읽는다`() {
            val product = catalogProduct(scope = stores(10, 20, 30))
            insert(product)

            assertSame(product, find())
            assertEquals(setOf(10L, 20L, 30L), targetStoreIds())
        }

        @Test
        fun `대상이 빈 한정 범위도 그대로 읽는다`() {
            val product = catalogProduct(scope = StoreScope.Limited(emptySet()))
            insert(product)

            assertSame(product, find())
            assertEquals(emptySet(), targetStoreIds())
        }

        @Test
        fun `SKU가 없는 복제본을 읽는다`() {
            insert(catalogProduct(sku = null))

            assertNull(find()?.sku)
        }

        @ParameterizedTest
        @EnumSource(CatalogProductStatus::class)
        fun `모든 상태를 그대로 읽는다`(status: CatalogProductStatus) {
            insert(catalogProduct(status = status))

            assertEquals(status, find()?.status)
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `재고 추적 여부를 그대로 읽는다`(tracks: Boolean) {
            insert(catalogProduct(tracksInventory = tracks))

            assertEquals(tracks, find()?.tracksInventory)
        }

        @Test
        fun `삭제 표시된 복제본을 읽는다`() {
            insert(catalogProduct(deleted = true))

            assertTrue(find()?.deleted == true)
        }

        @ParameterizedTest
        @ValueSource(longs = [0, 1, 4_000_000_000L])
        fun `순번을 그대로 읽는다 (0과 32비트를 넘는 값 포함)`(sequence: Long) {
            insert(catalogProduct(sequence = sequence))

            assertEquals(sequence, find()?.sequence)
        }

        @Test
        fun `없는 상품은 null이다`() {
            assertNull(find(999))
            assertNull(tx { repository.findByIdForShare(ProductId(999)) })
        }

        @Test
        fun `다른 상품의 대상 매장이 섞이지 않는다`() {
            insert(catalogProduct(productId = ProductId(1), sku = Sku("DZ-00000001"), scope = stores(10, 20)))
            insert(catalogProduct(productId = ProductId(2), sku = Sku("DZ-00000002"), scope = stores(30)))

            assertEquals(stores(10, 20), find(1)?.scope)
            assertEquals(stores(30), find(2)?.scope)
        }

        @Test
        fun `공유 잠금으로도 같은 값을 읽는다`() {
            val product = catalogProduct(scope = stores(10, 20))
            insert(product)

            assertSame(product, tx { repository.findByIdForShare(ProductId(1)) })
        }
    }

    @Nested
    @DisplayName("insertIfAbsent")
    inner class InsertIfAbsent {
        @Test
        fun `처음에는 넣고 true를 돌려준다`() {
            assertTrue(insert(catalogProduct()))
            assertNotNull(find())
        }

        @Test
        fun `이미 있으면 false이고 기존 값과 대상 매장이 그대로다`() {
            insert(catalogProduct(status = CatalogProductStatus.ACTIVE, scope = stores(10, 20), sequence = 5))

            val inserted = insert(catalogProduct(status = CatalogProductStatus.DRAFT, scope = stores(99), sequence = 1))

            assertFalse(inserted)
            val stored = find()
            assertEquals(CatalogProductStatus.ACTIVE, stored?.status)
            assertEquals(stores(10, 20), stored?.scope)
            assertEquals(5, stored?.sequence)
            assertEquals(setOf(10L, 20L), targetStoreIds())
        }

        @Test
        fun `다른 상품이 같은 SKU를 쓰면 거부하고 아무것도 넣지 않는다`() {
            insert(catalogProduct(productId = ProductId(1), sku = Sku("DZ-00000001"), scope = stores(10)))

            assertFailsWith<IllegalStateException> {
                insert(catalogProduct(productId = ProductId(2), sku = Sku("DZ-00000001"), scope = stores(20)))
            }

            assertNull(find(2))
            assertEquals(emptySet(), targetStoreIds(2))
        }

        @Test
        fun `SKU가 없는 상품은 여러 개 넣을 수 있다`() {
            assertTrue(insert(catalogProduct(productId = ProductId(1), sku = null)))
            assertTrue(insert(catalogProduct(productId = ProductId(2), sku = null)))
        }
    }

    @Nested
    @DisplayName("save")
    inner class Save {
        private fun saved(
            productId: Long = 1,
            block: (CatalogProduct) -> CatalogProduct,
        ) {
            val current = checkNotNull(tx { repository.findByIdForUpdate(ProductId(productId)) })
            tx { repository.save(block(current)) }
        }

        @Test
        fun `상태, SKU, 순번, 마지막 이벤트 시각을 교체한다`() {
            insert(catalogProduct(status = CatalogProductStatus.DRAFT, sku = null, sequence = 1))
            val later = Instant.parse("2026-10-15T09:00:00Z")

            saved {
                catalogProduct(
                    status = CatalogProductStatus.ACTIVE,
                    sku = Sku("DZ-00000009"),
                    sequence = 7,
                    lastEventAt = later,
                )
            }

            val stored = find()
            assertEquals(CatalogProductStatus.ACTIVE, stored?.status)
            assertEquals(Sku("DZ-00000009"), stored?.sku)
            assertEquals(7, stored?.sequence)
            assertEquals(later, stored?.lastEventAt)
        }

        @Test
        fun `판매 범위를 바꿀 때마다 대상 매장이 정확히 맞는다 (모든 매장 → 한정 → 다른 한정 → 모든 매장)`() {
            insert(catalogProduct(scope = StoreScope.All))

            saved { catalogProduct(scope = stores(10, 20)) }
            assertEquals(setOf(10L, 20L), targetStoreIds())
            assertEquals(stores(10, 20), find()?.scope)

            saved { catalogProduct(scope = stores(20, 30)) }
            assertEquals(setOf(20L, 30L), targetStoreIds())
            assertEquals(stores(20, 30), find()?.scope)

            saved { catalogProduct(scope = StoreScope.All) }
            assertEquals(emptySet(), targetStoreIds())
            assertEquals(StoreScope.All, find()?.scope)
        }

        @Test
        fun `한정 범위를 같은 값으로 다시 저장해도 대상 매장이 그대로다`() {
            insert(catalogProduct(scope = stores(10, 20)))

            saved { catalogProduct(scope = stores(10, 20), sequence = 2) }

            assertEquals(setOf(10L, 20L), targetStoreIds())
        }

        @Test
        fun `삭제 표시를 저장한다`() {
            insert(catalogProduct(scope = stores(10)))

            saved { catalogProduct(scope = stores(10), deleted = true) }

            assertTrue(find()?.deleted == true)
        }

        @Test
        fun `updated_at은 갱신하고 created_at은 바꾸지 않는다`() {
            insert(catalogProduct())
            val (createdBefore, updatedBefore) = timestamps()

            saved { catalogProduct(sequence = 2) }

            val (createdAfter, updatedAfter) = timestamps()
            assertEquals(createdBefore, createdAfter)
            assertTrue(updatedAfter.isAfter(updatedBefore), "updated_at이 갱신되지 않았습니다: $updatedBefore → $updatedAfter")
        }

        @Test
        fun `재고 추적 여부는 저장하지 않는다 (바뀌지 않는 값)`() {
            insert(catalogProduct(tracksInventory = true))

            saved { catalogProduct(tracksInventory = false, sequence = 2) }

            assertEquals(true, find()?.tracksInventory)
            assertEquals(2, find()?.sequence)
        }

        @Test
        fun `다른 상품의 값과 대상 매장은 건드리지 않는다`() {
            insert(catalogProduct(productId = ProductId(1), sku = Sku("DZ-00000001"), scope = stores(10)))
            insert(catalogProduct(productId = ProductId(2), sku = Sku("DZ-00000002"), scope = stores(30)))

            saved(1) { catalogProduct(productId = ProductId(1), sku = Sku("DZ-00000001"), scope = stores(11), sequence = 2) }

            assertEquals(stores(30), find(2)?.scope)
            assertEquals(1, find(2)?.sequence)
        }

        @Test
        fun `저장된 복제본이 없으면 거부하고 아무것도 넣지 않는다`() {
            assertFailsWith<IllegalStateException> { tx { repository.save(catalogProduct(scope = stores(10))) } }

            assertNull(find())
            assertEquals(emptySet(), targetStoreIds())
        }

        @Test
        fun `다른 상품이 쓰는 SKU로 바꾸면 거부하고 원래 값이 남는다`() {
            insert(catalogProduct(productId = ProductId(1), sku = Sku("DZ-00000001")))
            insert(catalogProduct(productId = ProductId(2), sku = Sku("DZ-00000002"), scope = stores(30)))

            assertFailsWith<Exception> {
                saved(2) { catalogProduct(productId = ProductId(2), sku = Sku("DZ-00000001"), scope = stores(40), sequence = 9) }
            }

            val stored = find(2)
            assertEquals(Sku("DZ-00000002"), stored?.sku)
            assertEquals(stores(30), stored?.scope)
            assertEquals(1, stored?.sequence)
        }
    }

    @Nested
    @DisplayName("findByIdsForShare")
    inner class FindByIdsForShare {
        private fun findAll(vararg ids: Long) = tx { repository.findByIdsForShare(ids.map { ProductId(it) }) }

        private fun seedProducts() {
            insert(catalogProduct(productId = ProductId(1), sku = Sku("DZ-00000001"), scope = StoreScope.All))
            insert(catalogProduct(productId = ProductId(2), sku = Sku("DZ-00000002"), scope = stores(10, 20)))
            insert(catalogProduct(productId = ProductId(3), sku = Sku("DZ-00000003"), scope = stores(30)))
            insert(catalogProduct(productId = ProductId(4), sku = Sku("DZ-00000004"), scope = StoreScope.Limited(emptySet())))
        }

        @Test
        fun `요청한 순서와 관계없이 상품 ID 순서로 돌려준다`() {
            seedProducts()

            assertEquals(listOf(1L, 2L, 3L), findAll(3, 1, 2).map { it.productId.value })
        }

        @Test
        fun `없는 ID는 결과에서 빠진다`() {
            seedProducts()

            assertEquals(listOf(2L, 4L), findAll(999, 4, 2, 998).map { it.productId.value })
        }

        @Test
        fun `같은 ID가 여러 번 와도 한 번만 돌려준다`() {
            seedProducts()

            assertEquals(listOf(1L, 2L), findAll(2, 1, 2, 1).map { it.productId.value })
        }

        @Test
        fun `빈 목록이면 빈 결과다`() {
            seedProducts()

            assertEquals(emptyList(), findAll())
        }

        @Test
        fun `판매 범위를 상품마다 그대로 읽는다 (모든 매장, 여러 매장, 한 매장, 빈 한정)`() {
            seedProducts()

            val scopes = findAll(1, 2, 3, 4).associate { it.productId.value to it.scope }

            assertEquals(StoreScope.All, scopes[1])
            assertEquals(stores(10, 20), scopes[2])
            assertEquals(stores(30), scopes[3])
            assertEquals(StoreScope.Limited(emptySet()), scopes[4])
        }

        @Test
        fun `한 상품씩 읽은 값과 같다`() {
            seedProducts()

            findAll(1, 2, 3, 4).forEach { product ->
                assertSame(product, tx { repository.findByIdForShare(product.productId) })
            }
        }

        @Test
        fun `삭제된 상품도 읽는다 (판단은 호출한 쪽이 한다)`() {
            insert(catalogProduct(deleted = true))

            assertEquals(true, findAll(1).single().deleted)
        }
    }

    @Nested
    @DisplayName("findActiveProductsCoveringStore")
    inner class FindActiveProductsCoveringStore {
        private val store = StoreId(10)

        private fun listed(storeId: StoreId = store) = tx { repository.findActiveProductsCoveringStore(storeId) }

        private fun ids(storeId: StoreId = store) = listed(storeId).map { it.productId.value }

        private fun seedProduct(
            id: Long,
            scope: StoreScope = StoreScope.All,
            status: CatalogProductStatus = CatalogProductStatus.ACTIVE,
            deleted: Boolean = false,
            tracksInventory: Boolean = false,
        ) = insert(
            catalogProduct(
                productId = ProductId(id),
                sku = Sku("DZ-%08d".format(id)),
                scope = scope,
                status = status,
                deleted = deleted,
                tracksInventory = tracksInventory,
            ),
        )

        @Test
        fun `모든 매장 판매 상품과 이 매장이 대상인 한정 상품을 고른다`() {
            seedProduct(1, scope = StoreScope.All)
            seedProduct(2, scope = stores(10, 20))
            seedProduct(3, scope = stores(20))

            assertEquals(listOf(1L, 2L), ids())
        }

        @Test
        fun `한정 상품의 대상이 아닌 매장은 고르지 않는다 (빈 한정 포함)`() {
            seedProduct(1, scope = stores(20))
            seedProduct(2, scope = StoreScope.Limited(emptySet()))

            assertEquals(emptyList(), ids())
        }

        @Test
        fun `ACTIVE가 아닌 상품과 삭제된 상품은 고르지 않는다`() {
            seedProduct(1, status = CatalogProductStatus.DRAFT)
            seedProduct(2, status = CatalogProductStatus.DISCONTINUED)
            seedProduct(3, deleted = true)
            seedProduct(4)

            assertEquals(listOf(4L), ids())
        }

        @Test
        fun `상품 ID 순으로 돌려주고 재고 추적 여부를 담는다`() {
            seedProduct(30, tracksInventory = true)
            seedProduct(10, tracksInventory = false)
            seedProduct(20, scope = stores(10), tracksInventory = true)

            assertEquals(
                listOf(
                    SellableProductSummary(ProductId(10), false),
                    SellableProductSummary(ProductId(20), true),
                    SellableProductSummary(ProductId(30), true),
                ),
                listed(),
            )
        }

        @Test
        fun `상품이 없으면 빈 결과다`() {
            assertEquals(emptyList(), ids())
        }

        @Test
        fun `매장마다 자기 대상 상품만 고른다`() {
            seedProduct(1, scope = stores(10))
            seedProduct(2, scope = stores(20))
            seedProduct(3, scope = stores(10, 20))

            assertEquals(listOf(1L, 3L), ids(StoreId(10)))
            assertEquals(listOf(2L, 3L), ids(StoreId(20)))
            assertEquals(emptyList(), ids(StoreId(30)))
        }

        @Test
        fun `상태, 판매 범위, 삭제 표시의 모든 조합에서 도메인 판단과 같다`() {
            val statuses = CatalogProductStatus.entries
            val scopes = listOf(StoreScope.All, stores(10, 20), stores(20), StoreScope.Limited(emptySet()))
            val deletions = listOf(false, true)
            var id = 0L
            val expected = mutableListOf<Long>()
            for (status in statuses) {
                for (scope in scopes) {
                    for (deleted in deletions) {
                        id += 1
                        seedProduct(id, scope = scope, status = status, deleted = deleted)
                        if (status == CatalogProductStatus.ACTIVE && !deleted && scope.covers(store)) expected += id
                    }
                }
            }

            assertEquals(24, id)
            assertEquals(expected, ids())
            assertTrue(expected.isNotEmpty() && expected.size < id)
        }

        @Test
        fun `많은 상품이 섞여 있어도 기대한 상품만 고른다`() {
            val expected = mutableListOf<Long>()
            for (i in 1L..300L) {
                val scope =
                    when (i % 3) {
                        0L -> StoreScope.All
                        1L -> stores(10, 20)
                        else -> stores(20)
                    }
                val status = if (i % 7 == 0L) CatalogProductStatus.DRAFT else CatalogProductStatus.ACTIVE
                seedProduct(i, scope = scope, status = status)
                if (status == CatalogProductStatus.ACTIVE && scope.covers(store)) expected += i
            }

            assertEquals(expected, ids())
        }
    }
}
