package com.dozycoffee.store.catalogproduct.adapter.outbound.persistence

import com.dozycoffee.store.catalogproduct.application.port.outbound.CatalogProductRepository
import com.dozycoffee.store.catalogproduct.domain.CatalogProduct
import com.dozycoffee.store.catalogproduct.domain.StoreScope
import com.dozycoffee.store.core.ProductId
import com.dozycoffee.store.core.Sku
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.fixture.catalogProduct
import com.dozycoffee.store.fixture.snapshot
import com.dozycoffee.store.support.ApplicationTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 상품 행 단위의 직렬화 규칙을 실제 DB에서 확인한다(docs/erd.md 동시성 처리).
 * "기다린다"는 잠금을 쥔 트랜잭션을 열어 둔 채 짧은 시간 안에 끝나지 않는 것으로 보고, 풀어 준 뒤 끝나는 것까지 확인한다.
 */
@DisplayName("ExposedCatalogProductRepository 동시성")
class ExposedCatalogProductRepositoryConcurrencyTest : ApplicationTest() {
    @Autowired
    private lateinit var repository: CatalogProductRepository

    private val productId = ProductId(1)

    private fun stores(vararg ids: Long) = StoreScope.Limited(ids.map { StoreId(it) }.toSet())

    private fun insert(product: CatalogProduct) = tx { repository.insertIfAbsent(product) }

    private fun find(id: Long = 1) = tx { repository.findByIdForUpdate(ProductId(id)) }

    private fun count(sql: String): Long =
        runBlocking {
            databaseClient
                .sql(sql)
                .map { row -> row.get(0, java.lang.Long::class.java)!!.toLong() }
                .one()
                .awaitSingle()
        }

    /** 잠금을 쥔 트랜잭션을 [release]가 완료될 때까지 열어 둔다. [work]로 잠금을 얻은 뒤에 돌려준다. */
    private suspend fun CoroutineScope.hold(
        release: CompletableDeferred<Unit>,
        work: suspend () -> Unit,
    ): Deferred<Unit> {
        val locked = CompletableDeferred<Unit>()
        val holder =
            async(Dispatchers.Default) {
                transactionRunner.inTransaction {
                    work()
                    locked.complete(Unit)
                    release.await()
                }
            }
        withTimeout(5_000) { locked.await() }
        return holder
    }

    private suspend fun <T> Deferred<T>.assertWaiting() {
        assertNull(withTimeoutOrNull(500) { await() }, "잠금이 풀리기 전에는 끝나면 안 됩니다")
    }

    @Nested
    @DisplayName("FOR UPDATE 직렬화")
    inner class ForUpdate {
        @Test
        fun `같은 상품의 잠금 조회는 앞의 트랜잭션이 끝날 때까지 기다린다`() {
            insert(catalogProduct())

            runBlocking {
                val release = CompletableDeferred<Unit>()
                val holder = hold(release) { repository.findByIdForUpdate(productId) }
                val waiter = async(Dispatchers.Default) { transactionRunner.inTransaction { repository.findByIdForUpdate(productId) } }

                waiter.assertWaiting()

                release.complete(Unit)
                holder.await()
                assertNotNull(withTimeout(5_000) { waiter.await() })
            }
        }

        @Test
        fun `다른 상품의 잠금은 서로 기다리지 않는다`() {
            insert(catalogProduct(productId = ProductId(1), sku = Sku("DZ-00000001")))
            insert(catalogProduct(productId = ProductId(2), sku = Sku("DZ-00000002")))

            runBlocking {
                val release = CompletableDeferred<Unit>()
                val holder = hold(release) { repository.findByIdForUpdate(ProductId(1)) }

                val other = async(Dispatchers.Default) { transactionRunner.inTransaction { repository.findByIdForUpdate(ProductId(2)) } }

                assertNotNull(withTimeout(5_000) { other.await() })
                release.complete(Unit)
                holder.await()
            }
        }

        @Test
        fun `같은 상품의 이벤트를 동시에 반영해도 순번이 큰 상태만 남는다`() {
            insert(catalogProduct(scope = stores(1), sequence = 1))
            val sequences = (2L..21L).toList().shuffled()

            runBlocking {
                withTimeout(30_000) {
                    sequences
                        .map { n ->
                            async(Dispatchers.Default) {
                                transactionRunner.inTransaction {
                                    val product = checkNotNull(repository.findByIdForUpdate(productId))
                                    val result = product.apply(snapshot(scope = stores(n), sequence = n))
                                    if (result.applied) repository.save(product)
                                }
                            }
                        }.awaitAll()
                }
            }

            val stored = find()
            assertEquals(21, stored?.sequence)
            assertEquals(stores(21), stored?.scope)
            assertEquals(1, count("SELECT count(*) FROM catalog_product_target_stores WHERE product_id = 1"))
        }
    }

    @Nested
    @DisplayName("FOR SHARE와 FOR UPDATE")
    inner class ForShare {
        @Test
        fun `공유 잠금을 쥔 동안 잠금 조회(갱신)는 기다린다`() {
            insert(catalogProduct())

            runBlocking {
                val release = CompletableDeferred<Unit>()
                val holder = hold(release) { repository.findByIdForShare(productId) }
                val updater = async(Dispatchers.Default) { transactionRunner.inTransaction { repository.findByIdForUpdate(productId) } }

                updater.assertWaiting()

                release.complete(Unit)
                holder.await()
                assertNotNull(withTimeout(5_000) { updater.await() })
            }
        }

        @Test
        fun `갱신 잠금을 쥔 동안 공유 잠금 조회는 기다린다`() {
            insert(catalogProduct())

            runBlocking {
                val release = CompletableDeferred<Unit>()
                val holder = hold(release) { repository.findByIdForUpdate(productId) }
                val reader = async(Dispatchers.Default) { transactionRunner.inTransaction { repository.findByIdForShare(productId) } }

                reader.assertWaiting()

                release.complete(Unit)
                holder.await()
                assertNotNull(withTimeout(5_000) { reader.await() })
            }
        }

        @Test
        fun `판매 범위를 바꾸는 중이면 공유 잠금 조회는 끝난 뒤 새 범위를 본다`() {
            insert(catalogProduct(scope = stores(10), sequence = 1))

            runBlocking {
                val release = CompletableDeferred<Unit>()
                val holder =
                    hold(release) {
                        val product = checkNotNull(repository.findByIdForUpdate(productId))
                        product.apply(snapshot(scope = stores(20), sequence = 2))
                        repository.save(product)
                    }
                val reader = async(Dispatchers.Default) { transactionRunner.inTransaction { repository.findByIdForShare(productId) } }

                reader.assertWaiting()

                release.complete(Unit)
                holder.await()
                val seen = withTimeout(5_000) { reader.await() }
                assertEquals(stores(20), seen?.scope)
                assertEquals(2, seen?.sequence)
            }
        }

        @Test
        fun `공유 잠금끼리는 기다리지 않는다`() {
            insert(catalogProduct())

            runBlocking {
                val release = CompletableDeferred<Unit>()
                val holder = hold(release) { repository.findByIdForShare(productId) }

                val other = async(Dispatchers.Default) { transactionRunner.inTransaction { repository.findByIdForShare(productId) } }

                assertNotNull(withTimeout(5_000) { other.await() })
                release.complete(Unit)
                holder.await()
            }
        }
    }

    @Nested
    @DisplayName("없으면 넣고 다시 잠그는 흐름")
    inner class InsertIfAbsent {
        @Test
        fun `같은 상품을 동시에 넣으면 하나만 성공하고 대상 매장은 한 벌이다`() {
            val results =
                runBlocking {
                    withTimeout(30_000) {
                        (1..6)
                            .map {
                                async(Dispatchers.Default) {
                                    transactionRunner.inTransaction { repository.insertIfAbsent(catalogProduct(scope = stores(10, 20))) }
                                }
                            }.awaitAll()
                    }
                }

            assertEquals(1, results.count { it })
            assertEquals(1, count("SELECT count(*) FROM catalog_products"))
            assertEquals(2, count("SELECT count(*) FROM catalog_product_target_stores"))
            assertEquals(stores(10, 20), find()?.scope)
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
                                    val event =
                                        snapshot(
                                            productId = ProductId(id),
                                            sku = Sku("DZ-%08d".format(id)),
                                            scope = stores(sequence),
                                            sequence = sequence,
                                        )
                                    transactionRunner.inTransaction {
                                        var product = repository.findByIdForUpdate(event.productId)
                                        if (product == null) {
                                            if (repository.insertIfAbsent(CatalogProduct.replicate(event))) return@inTransaction
                                            product = checkNotNull(repository.findByIdForUpdate(event.productId))
                                        }
                                        if (product.apply(event).applied) repository.save(product)
                                    }
                                }
                            }
                        }.awaitAll()
                }
            }

            ids.forEach { id ->
                val stored = find(id)
                assertEquals(2, stored?.sequence, "상품 $id")
                assertEquals(stores(2), stored?.scope, "상품 $id")
            }
            assertEquals(10, count("SELECT count(*) FROM catalog_products"))
            assertEquals(10, count("SELECT count(*) FROM catalog_product_target_stores"))
            assertTrue(ids.all { count("SELECT count(*) FROM catalog_product_target_stores WHERE product_id = $it") == 1L })
        }
    }
}
