package com.dozycoffee.store.catalogproduct.application.service

import com.dozycoffee.store.catalogproduct.application.port.inbound.ApplyCatalogProductSnapshotUseCase
import com.dozycoffee.store.catalogproduct.application.port.inbound.CheckProductSellableUseCase
import com.dozycoffee.store.catalogproduct.application.port.inbound.result.SellableProductResult
import com.dozycoffee.store.catalogproduct.application.port.outbound.CatalogProductRepository
import com.dozycoffee.store.catalogproduct.domain.CatalogProduct
import com.dozycoffee.store.catalogproduct.domain.CatalogProductStatus
import com.dozycoffee.store.catalogproduct.domain.StoreScope
import com.dozycoffee.store.catalogproduct.domain.exception.CatalogProductErrorCode
import com.dozycoffee.store.catalogproduct.domain.exception.ProductNotFoundException
import com.dozycoffee.store.core.ProductId
import com.dozycoffee.store.core.Sku
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.fixture.applyCommand
import com.dozycoffee.store.fixture.catalogProduct
import com.dozycoffee.store.support.ApplicationTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("CatalogProductService — 이 매장에서 상품을 다룰 수 있는지 (요구사항 3.1)")
class CatalogProductSellableServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var sellable: CheckProductSellableUseCase

    @Autowired
    private lateinit var apply: ApplyCatalogProductSnapshotUseCase

    @Autowired
    private lateinit var products: CatalogProductRepository

    private val store = StoreId(10)
    private val otherStore = StoreId(20)

    private fun stores(vararg ids: Long) = StoreScope.Limited(ids.map { StoreId(it) }.toSet())

    private fun seed(product: CatalogProduct) {
        tx { products.insertIfAbsent(product) }
    }

    private fun seedProduct(
        id: Long,
        scope: StoreScope = StoreScope.All,
        deleted: Boolean = false,
        status: CatalogProductStatus = CatalogProductStatus.ACTIVE,
        tracksInventory: Boolean = false,
    ) = seed(
        catalogProduct(
            productId = ProductId(id),
            sku = Sku("DZ-%08d".format(id)),
            scope = scope,
            deleted = deleted,
            status = status,
            tracksInventory = tracksInventory,
            sequence = 1,
        ),
    )

    private fun check(
        id: Long,
        storeId: StoreId = store,
    ) = runBlocking { sellable.check(storeId, ProductId(id)) }

    private fun checkAll(
        vararg ids: Long,
        storeId: StoreId = store,
    ) = runBlocking { sellable.checkAll(storeId, ids.map { ProductId(it) }) }

    private fun assertNotFound(
        id: Long,
        block: () -> Unit,
    ) {
        val e = assertFailsWith<ProductNotFoundException> { block() }
        assertEquals(CatalogProductErrorCode.PRODUCT_NOT_FOUND, e.errorCode)
        assertTrue("$id" in e.message!!, e.message)
    }

    @Nested
    @DisplayName("상품 하나")
    inner class Single {
        @Test
        fun `모든 매장에 판매하는 상품은 다룰 수 있다`() {
            seedProduct(1, scope = StoreScope.All)

            assertEquals(SellableProductResult(ProductId(1), tracksInventory = false), check(1))
        }

        @Test
        fun `한정 판매 상품은 대상 매장만 다룰 수 있다`() {
            seedProduct(1, scope = stores(10, 30))

            assertEquals(ProductId(1), check(1, store).productId)
            assertNotFound(1) { check(1, otherStore) }
        }

        @Test
        fun `대상이 없는 한정 판매 상품은 어느 매장도 다룰 수 없다`() {
            seedProduct(1, scope = StoreScope.Limited(emptySet()))

            assertNotFound(1) { check(1) }
        }

        @Test
        fun `없는 상품은 거부한다`() {
            assertNotFound(999) { check(999) }
        }

        @Test
        fun `삭제된 상품은 거부한다`() {
            seedProduct(1, deleted = true)

            assertNotFound(1) { check(1) }
        }

        @ParameterizedTest
        @EnumSource(CatalogProductStatus::class)
        fun `상품 상태와 관계없이 판매 범위에 있으면 다룰 수 있다 (Draft, Discontinued 포함)`(status: CatalogProductStatus) {
            seedProduct(1, status = status)

            assertEquals(ProductId(1), check(1).productId)
        }

        @Test
        fun `재고 추적 여부를 결과에 담는다`() {
            seedProduct(1, tracksInventory = true)
            seedProduct(2, tracksInventory = false)

            assertTrue(check(1).tracksInventory)
            assertEquals(false, check(2).tracksInventory)
        }

        @Test
        fun `없는 상품, 삭제된 상품, 판매 범위 밖의 상품은 같은 오류다 (존재 여부를 알아낼 수 없다)`() {
            seedProduct(1, deleted = true)
            seedProduct(2, scope = stores(30))

            val missing = assertFailsWith<ProductNotFoundException> { check(999) }
            val deleted = assertFailsWith<ProductNotFoundException> { check(1) }
            val outOfScope = assertFailsWith<ProductNotFoundException> { check(2) }

            assertEquals(missing.errorCode, deleted.errorCode)
            assertEquals(missing.errorCode, outOfScope.errorCode)
            assertEquals(missing::class, outOfScope::class)
        }
    }

    @Nested
    @DisplayName("여러 상품 (진열 순서 일괄 변경)")
    inner class All {
        @Test
        fun `모두 다룰 수 있으면 상품 ID 순서의 결과를 돌려준다`() {
            seedProduct(1)
            seedProduct(2, scope = stores(10))
            seedProduct(3, tracksInventory = true)

            val results = checkAll(3, 1, 2)

            assertEquals(listOf(1L, 2L, 3L), results.map { it.productId.value })
            assertEquals(listOf(false, false, true), results.map { it.tracksInventory })
        }

        @Test
        fun `같은 상품이 여러 번 와도 한 번만 확인한다`() {
            seedProduct(1)
            seedProduct(2)

            assertEquals(listOf(1L, 2L), checkAll(2, 1, 2, 1).map { it.productId.value })
        }

        @Test
        fun `빈 목록이면 빈 결과다`() {
            assertEquals(emptyList(), checkAll())
        }

        @Test
        fun `하나라도 다룰 수 없으면 전체를 거부한다`() {
            seedProduct(1)
            seedProduct(2, scope = stores(30))
            seedProduct(3)

            assertNotFound(2) { checkAll(1, 2, 3) }
        }

        @Test
        fun `어긋난 상품이 여럿이면 상품 ID가 가장 작은 상품으로 거부한다 (요청 순서와 관계없다)`() {
            seedProduct(1)
            seedProduct(2, scope = stores(30))
            seedProduct(3, deleted = true)

            assertNotFound(2) { checkAll(3, 999, 2, 1) }
            assertNotFound(2) { checkAll(1, 2, 3, 999) }
        }

        @Test
        fun `없는 상품과 삭제된 상품이 섞여도 거부한다`() {
            seedProduct(1, deleted = true)

            assertNotFound(1) { checkAll(1, 999) }
        }
    }

    /** 잠금 때문에 기다리는 요청이 남은 채 테스트가 실패해도 멈추지 않도록 시간을 제한한다. */
    @Nested
    @DisplayName("잠금 (판매 범위 정리와 겹칠 때)")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    inner class Locking {
        private suspend fun <T> Deferred<T>.assertWaiting() {
            assertNull(withTimeoutOrNull(500) { await() }, "잠금이 풀리기 전에는 끝나면 안 됩니다")
        }

        /**
         * [work]를 마친 트랜잭션을 `release`가 완료될 때까지 열어 둔 채 [body]를 실행한다.
         * [body]가 실패해도 트랜잭션을 풀어 준다. 잠금을 기다리는 요청이 남은 채로 끝나 테스트가 멈추는 일을 막는다.
         */
        private fun runHeld(
            work: suspend () -> Unit,
            body: suspend CoroutineScope.(release: CompletableDeferred<Unit>) -> Unit,
        ) = runBlocking {
            val locked = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val holder =
                async(Dispatchers.Default) {
                    transactionRunner.inTransaction {
                        work()
                        locked.complete(Unit)
                        release.await()
                    }
                }
            try {
                withTimeout(5_000) { locked.await() }
                body(release)
            } finally {
                release.complete(Unit)
            }
            holder.await()
        }

        @Test
        fun `확인한 트랜잭션이 끝날 때까지 같은 상품의 판매 범위 변경은 기다린다`() {
            seedProduct(1, scope = stores(10))

            runHeld(work = { sellable.check(store, ProductId(1)) }) { release ->
                val change =
                    async(Dispatchers.Default) {
                        apply.apply(applyCommand(productId = ProductId(1), sku = Sku("DZ-00000001"), scope = stores(20), sequence = 2))
                    }

                change.assertWaiting()
                assertEquals(stores(10), tx { products.findByIdForShare(ProductId(1)) }?.scope)

                release.complete(Unit)
                withTimeout(5_000) { change.await() }
                assertEquals(stores(20), tx { products.findByIdForShare(ProductId(1)) }?.scope)
            }
        }

        @Test
        fun `판매 범위를 바꾸는 중이면 확인은 끝난 뒤 새 판매 범위로 판단한다`() {
            seedProduct(1, scope = stores(10))

            runHeld(work = {
                apply.apply(applyCommand(productId = ProductId(1), sku = Sku("DZ-00000001"), scope = stores(20), sequence = 2))
            }) { release ->
                val oldScope = async(Dispatchers.Default) { runCatching { sellable.check(store, ProductId(1)) } }
                val newScope = async(Dispatchers.Default) { runCatching { sellable.check(otherStore, ProductId(1)) } }

                oldScope.assertWaiting()
                newScope.assertWaiting()

                release.complete(Unit)
                assertIs<ProductNotFoundException>(withTimeout(5_000) { oldScope.await() }.exceptionOrNull())
                assertNotNull(withTimeout(5_000) { newScope.await() }.getOrNull())
            }
        }

        @Test
        fun `여러 상품을 확인한 트랜잭션이 끝날 때까지 그중 하나의 판매 범위 변경은 기다린다`() {
            seedProduct(1, scope = stores(10))
            seedProduct(2, scope = stores(10))

            runHeld(work = { sellable.checkAll(store, listOf(ProductId(2), ProductId(1))) }) { release ->
                val change =
                    async(Dispatchers.Default) {
                        apply.apply(applyCommand(productId = ProductId(2), sku = Sku("DZ-00000002"), scope = stores(20), sequence = 2))
                    }

                change.assertWaiting()

                release.complete(Unit)
                withTimeout(5_000) { change.await() }
            }
        }
    }
}
