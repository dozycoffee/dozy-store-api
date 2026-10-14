package com.dozycoffee.store.catalogproduct.application.service

import com.dozycoffee.store.catalogproduct.application.port.inbound.ApplyCatalogProductSnapshotUseCase
import com.dozycoffee.store.catalogproduct.application.port.inbound.result.ApplyOutcome
import com.dozycoffee.store.catalogproduct.application.port.outbound.CatalogProductRepository
import com.dozycoffee.store.catalogproduct.domain.CatalogProduct
import com.dozycoffee.store.catalogproduct.domain.CatalogProductStatus
import com.dozycoffee.store.catalogproduct.domain.StoreScope
import com.dozycoffee.store.catalogproduct.domain.event.CatalogProductScopeChanged
import com.dozycoffee.store.core.DomainEvent
import com.dozycoffee.store.core.ProductId
import com.dozycoffee.store.core.Sku
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.fixture.applyCommand
import com.dozycoffee.store.fixture.catalogProduct
import com.dozycoffee.store.support.ApplicationTest
import com.dozycoffee.store.support.RecordingDomainEventHandler
import com.dozycoffee.store.support.WithRecordingDomainEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@WithRecordingDomainEvents
@DisplayName("CatalogProductService — S5 Catalog 상품 변경의 반영")
class CatalogProductServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var useCase: ApplyCatalogProductSnapshotUseCase

    @Autowired
    private lateinit var products: CatalogProductRepository

    @Autowired
    private lateinit var recorder: RecordingDomainEventHandler

    @BeforeEach
    fun resetRecorder() = recorder.reset()

    private fun stores(vararg ids: Long) = StoreScope.Limited(ids.map { StoreId(it) }.toSet())

    private fun seed(product: CatalogProduct) {
        tx { products.insertIfAbsent(product) }
    }

    private fun stored(id: Long = 1): CatalogProduct? = tx { products.findByIdForUpdate(ProductId(id)) }

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

    private fun count(sql: String): Long =
        runBlocking {
            databaseClient
                .sql(sql)
                .map { row -> row.get(0, java.lang.Long::class.java)!!.toLong() }
                .one()
                .awaitSingle()
        }

    @Nested
    @DisplayName("S5 2c 처음 보는 상품")
    inner class Replicate {
        @Test
        fun `복제하고 판매 범위 바뀜 이벤트를 처리한다`() {
            val outcome =
                runBlocking {
                    useCase.apply(
                        applyCommand(
                            sku = Sku("DZ-00000007"),
                            status = CatalogProductStatus.DRAFT,
                            scope = stores(10, 20),
                            tracksInventory = true,
                            sequence = 0,
                            occurredAt = Instant.parse("2026-10-14T02:00:00Z"),
                        ),
                    )
                }

            assertEquals(ApplyOutcome.REPLICATED, outcome)
            val product = stored()
            assertNotNull(product)
            assertEquals(Sku("DZ-00000007"), product.sku)
            assertEquals(CatalogProductStatus.DRAFT, product.status)
            assertEquals(stores(10, 20), product.scope)
            assertTrue(product.tracksInventory)
            assertEquals(0, product.sequence)
            assertEquals(Instant.parse("2026-10-14T02:00:00Z"), product.lastEventAt)
            assertEquals(listOf<DomainEvent>(CatalogProductScopeChanged(ProductId(1), stores(10, 20))), recorder.events)
        }

        @Test
        fun `SKU가 없는 상품도 복제한다`() {
            runBlocking { useCase.apply(applyCommand(sku = null)) }

            assertNull(stored()?.sku)
        }
    }

    @Nested
    @DisplayName("반영")
    inner class Apply {
        @Test
        fun `순번이 큰 이벤트는 상태 전체로 교체하고 저장한다`() {
            seed(catalogProduct(status = CatalogProductStatus.DRAFT, sku = null, sequence = 1))
            val occurredAt = Instant.parse("2026-10-15T09:00:00Z")

            val outcome =
                runBlocking {
                    useCase.apply(
                        applyCommand(status = CatalogProductStatus.ACTIVE, sku = Sku("DZ-00000009"), sequence = 6, occurredAt = occurredAt),
                    )
                }

            assertEquals(ApplyOutcome.APPLIED, outcome)
            val product = stored()
            assertEquals(CatalogProductStatus.ACTIVE, product?.status)
            assertEquals(Sku("DZ-00000009"), product?.sku)
            assertEquals(6, product?.sequence)
            assertEquals(occurredAt, product?.lastEventAt)
        }

        @Test
        fun `S5 2a 순번 6을 반영한 뒤 순번 5가 오면 무시하고 저장된 상태가 그대로다`() {
            seed(catalogProduct(status = CatalogProductStatus.DRAFT, sequence = 4))
            runBlocking { useCase.apply(applyCommand(status = CatalogProductStatus.ACTIVE, sequence = 6)) }
            recorder.reset()

            val outcome = runBlocking { useCase.apply(applyCommand(status = CatalogProductStatus.DISCONTINUED, sequence = 5)) }

            assertEquals(ApplyOutcome.IGNORED, outcome)
            assertEquals(CatalogProductStatus.ACTIVE, stored()?.status)
            assertEquals(6, stored()?.sequence)
            assertTrue(recorder.events.isEmpty())
        }

        @Test
        fun `같은 순번의 중복 이벤트는 무시한다`() {
            seed(catalogProduct(status = CatalogProductStatus.DRAFT, sequence = 5))

            val outcome = runBlocking { useCase.apply(applyCommand(status = CatalogProductStatus.ACTIVE, sequence = 5)) }

            assertEquals(ApplyOutcome.IGNORED, outcome)
            assertEquals(CatalogProductStatus.DRAFT, stored()?.status)
        }

        @Test
        fun `S5 2b 삭제된 복제본은 더 큰 순번도 반영하지 않는다`() {
            seed(catalogProduct(deleted = true, status = CatalogProductStatus.DRAFT, sequence = 5))

            val outcome =
                runBlocking { useCase.apply(applyCommand(status = CatalogProductStatus.ACTIVE, scope = stores(1), sequence = 99)) }

            assertEquals(ApplyOutcome.IGNORED, outcome)
            val product = stored()
            assertTrue(product?.deleted == true)
            assertEquals(CatalogProductStatus.DRAFT, product?.status)
            assertTrue(recorder.events.isEmpty())
        }

        @Test
        fun `판매 범위를 ALL, 한정, 다른 한정, ALL로 반영할 때마다 대상 매장 테이블이 맞다`() {
            seed(catalogProduct(scope = StoreScope.All, sequence = 1))

            runBlocking { useCase.apply(applyCommand(scope = stores(10, 20), sequence = 2)) }
            assertEquals(setOf(10L, 20L), targetStoreIds())
            runBlocking { useCase.apply(applyCommand(scope = stores(20, 30), sequence = 3)) }
            assertEquals(setOf(20L, 30L), targetStoreIds())
            runBlocking { useCase.apply(applyCommand(scope = StoreScope.All, sequence = 4)) }
            assertEquals(emptySet(), targetStoreIds())
            assertEquals(StoreScope.All, stored()?.scope)
        }
    }

    @Nested
    @DisplayName("도메인 이벤트 처리")
    inner class Events {
        @Test
        fun `판매 범위가 바뀌면 판매 범위 바뀜 이벤트를 처리한다`() {
            seed(catalogProduct(scope = StoreScope.All, sequence = 1))

            runBlocking { useCase.apply(applyCommand(scope = stores(10), sequence = 2)) }

            assertEquals(listOf<DomainEvent>(CatalogProductScopeChanged(ProductId(1), stores(10))), recorder.events)
        }

        @Test
        fun `판매 범위가 같은 이벤트는 반영하지만 이벤트를 처리하지 않는다 (활성화, 같은 범위로 다시 지정)`() {
            seed(catalogProduct(status = CatalogProductStatus.DRAFT, scope = stores(10), sequence = 1))

            val activated =
                runBlocking { useCase.apply(applyCommand(status = CatalogProductStatus.ACTIVE, scope = stores(10), sequence = 2)) }
            val sameAgain =
                runBlocking { useCase.apply(applyCommand(status = CatalogProductStatus.ACTIVE, scope = stores(10), sequence = 3)) }

            assertEquals(ApplyOutcome.APPLIED, activated)
            assertEquals(ApplyOutcome.APPLIED, sameAgain)
            assertTrue(recorder.events.isEmpty())
        }

        @Test
        fun `핸들러가 실패하면 반영이 롤백되어 저장된 상태가 그대로다`() {
            seed(catalogProduct(status = CatalogProductStatus.DRAFT, scope = StoreScope.All, sequence = 1))
            recorder.failOn = CatalogProductScopeChanged::class

            assertFailsWith<IllegalStateException> {
                runBlocking { useCase.apply(applyCommand(status = CatalogProductStatus.ACTIVE, scope = stores(10), sequence = 2)) }
            }

            val product = stored()
            assertEquals(CatalogProductStatus.DRAFT, product?.status)
            assertEquals(StoreScope.All, product?.scope)
            assertEquals(1, product?.sequence)
            assertEquals(emptySet(), targetStoreIds())
        }

        @Test
        fun `처음 복제할 때 핸들러가 실패하면 복제본이 남지 않는다`() {
            recorder.failOn = CatalogProductScopeChanged::class

            assertFailsWith<IllegalStateException> { runBlocking { useCase.apply(applyCommand(scope = stores(10))) } }

            assertNull(stored())
            assertEquals(0, count("SELECT count(*) FROM catalog_products"))
            assertEquals(0, count("SELECT count(*) FROM catalog_product_target_stores"))
        }
    }

    @Nested
    @DisplayName("Catalog의 계약 위반")
    inner class ContractViolations {
        @Test
        fun `재고 추적 여부가 바뀐 이벤트는 거부하고 저장된 상태가 그대로다`() {
            seed(catalogProduct(tracksInventory = false, status = CatalogProductStatus.DRAFT, sequence = 1))

            assertFailsWith<IllegalStateException> {
                runBlocking { useCase.apply(applyCommand(tracksInventory = true, status = CatalogProductStatus.ACTIVE, sequence = 2)) }
            }

            val product = stored()
            assertEquals(false, product?.tracksInventory)
            assertEquals(CatalogProductStatus.DRAFT, product?.status)
            assertEquals(1, product?.sequence)
            assertTrue(recorder.events.isEmpty())
        }

        @Test
        fun `다른 상품이 쓰는 SKU를 보내면 거부하고 아무것도 저장하지 않는다`() {
            seed(catalogProduct(productId = ProductId(1), sku = Sku("DZ-00000001")))

            assertFailsWith<IllegalStateException> {
                runBlocking { useCase.apply(applyCommand(productId = ProductId(2), sku = Sku("DZ-00000001"), scope = stores(10))) }
            }

            assertNull(stored(2))
            assertEquals(emptySet(), targetStoreIds(2))
            assertTrue(recorder.events.isEmpty())
        }
    }

    @Nested
    @DisplayName("동시에 반영")
    inner class Concurrency {
        @Test
        fun `같은 상품의 이벤트를 동시에 반영해도 순번이 큰 상태가 남는다`() {
            seed(catalogProduct(scope = stores(1), sequence = 1))

            runBlocking {
                withTimeout(30_000) {
                    (2L..21L)
                        .toList()
                        .shuffled()
                        .map { n -> async(Dispatchers.Default) { useCase.apply(applyCommand(scope = stores(n), sequence = n)) } }
                        .awaitAll()
                }
            }

            val product = stored()
            assertEquals(21, product?.sequence)
            assertEquals(stores(21), product?.scope)
            assertEquals(setOf(21L), targetStoreIds())
        }

        @Test
        fun `처음 보는 상품의 이벤트가 동시에 와도 모두 반영되고 순번이 큰 상태가 남는다`() {
            val ids = (1L..10L).toList()

            runBlocking {
                withTimeout(60_000) {
                    ids
                        .flatMap { id ->
                            listOf(1L, 2L).map { sequence ->
                                async(Dispatchers.Default) {
                                    useCase.apply(
                                        applyCommand(
                                            productId = ProductId(id),
                                            sku = Sku("DZ-%08d".format(id)),
                                            scope = stores(sequence),
                                            sequence = sequence,
                                        ),
                                    )
                                }
                            }
                        }.awaitAll()
                }
            }

            ids.forEach { id ->
                val product = stored(id)
                assertEquals(2, product?.sequence, "상품 $id")
                assertEquals(stores(2), product?.scope, "상품 $id")
                assertEquals(setOf(2L), targetStoreIds(id), "상품 $id")
            }
            assertEquals(10, count("SELECT count(*) FROM catalog_products"))
        }
    }
}
