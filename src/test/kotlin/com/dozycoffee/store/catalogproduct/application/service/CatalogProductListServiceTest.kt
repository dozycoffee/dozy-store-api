package com.dozycoffee.store.catalogproduct.application.service

import com.dozycoffee.store.catalogproduct.application.port.inbound.ApplyCatalogProductSnapshotUseCase
import com.dozycoffee.store.catalogproduct.application.port.inbound.ListSellableProductsUseCase
import com.dozycoffee.store.catalogproduct.application.port.inbound.MarkCatalogProductDeletedUseCase
import com.dozycoffee.store.catalogproduct.application.port.inbound.result.SellableProductResult
import com.dozycoffee.store.catalogproduct.application.port.outbound.CatalogProductRepository
import com.dozycoffee.store.catalogproduct.domain.CatalogProduct
import com.dozycoffee.store.catalogproduct.domain.CatalogProductStatus
import com.dozycoffee.store.catalogproduct.domain.StoreScope
import com.dozycoffee.store.core.ProductId
import com.dozycoffee.store.core.Sku
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.fixture.applyCommand
import com.dozycoffee.store.fixture.catalogProduct
import com.dozycoffee.store.fixture.deleteCommand
import com.dozycoffee.store.support.ApplicationTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

@DisplayName("CatalogProductService — 이 매장에서 판매할 수 있는 상품 목록 (요구사항 5.1)")
class CatalogProductListServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var list: ListSellableProductsUseCase

    @Autowired
    private lateinit var apply: ApplyCatalogProductSnapshotUseCase

    @Autowired
    private lateinit var delete: MarkCatalogProductDeletedUseCase

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
        status: CatalogProductStatus = CatalogProductStatus.ACTIVE,
        deleted: Boolean = false,
        tracksInventory: Boolean = false,
    ) = seed(
        catalogProduct(
            productId = ProductId(id),
            sku = Sku("DZ-%08d".format(id)),
            scope = scope,
            status = status,
            deleted = deleted,
            tracksInventory = tracksInventory,
            sequence = 1,
        ),
    )

    private fun ids(storeId: StoreId = store) = runBlocking { list.list(storeId) }.map { it.productId.value }

    @Nested
    @DisplayName("규칙")
    inner class Rules {
        @Test
        fun `ACTIVE이고 판매 범위가 이 매장을 포함하는 상품만 상품 ID 순으로 돌려준다`() {
            seedProduct(30, scope = StoreScope.All)
            seedProduct(10, scope = stores(10))
            seedProduct(20, scope = stores(20))
            seedProduct(40, status = CatalogProductStatus.DRAFT)
            seedProduct(50, status = CatalogProductStatus.DISCONTINUED)
            seedProduct(60, deleted = true)

            assertEquals(listOf(10L, 30L), ids())
        }

        @Test
        fun `재고 추적 여부를 담는다`() {
            seedProduct(1, tracksInventory = true)
            seedProduct(2, tracksInventory = false)

            assertEquals(
                listOf(SellableProductResult(ProductId(1), true), SellableProductResult(ProductId(2), false)),
                runBlocking { list.list(store) },
            )
        }

        @Test
        fun `상품이 없으면 빈 목록이다`() {
            assertEquals(emptyList(), ids())
        }

        @Test
        fun `한 매장의 목록이 다른 매장에 영향을 주지 않는다`() {
            seedProduct(1, scope = stores(10))
            seedProduct(2, scope = stores(20))
            seedProduct(3, scope = StoreScope.All)

            assertEquals(listOf(1L, 3L), ids(store))
            assertEquals(listOf(2L, 3L), ids(otherStore))
        }
    }

    @Nested
    @DisplayName("S5 상품 변경이 반영된 뒤")
    inner class AfterChanges {
        @Test
        fun `활성화되면 목록에 나타나고 단종되면 사라지며 재활성화되면 다시 나타난다`() {
            seedProduct(1, status = CatalogProductStatus.DRAFT)
            assertEquals(emptyList(), ids())

            runBlocking { apply.apply(applyCommand(sku = Sku("DZ-00000001"), status = CatalogProductStatus.ACTIVE, sequence = 2)) }
            assertEquals(listOf(1L), ids())

            runBlocking { apply.apply(applyCommand(sku = Sku("DZ-00000001"), status = CatalogProductStatus.DISCONTINUED, sequence = 3)) }
            assertEquals(emptyList(), ids())

            runBlocking { apply.apply(applyCommand(sku = Sku("DZ-00000001"), status = CatalogProductStatus.ACTIVE, sequence = 4)) }
            assertEquals(listOf(1L), ids())
        }

        @Test
        fun `판매 범위에서 이 매장이 빠지면 사라지고 다시 들어오면 나타난다`() {
            seedProduct(1, scope = stores(10, 20))
            assertEquals(listOf(1L), ids(store))

            runBlocking { apply.apply(applyCommand(sku = Sku("DZ-00000001"), scope = stores(20), sequence = 2)) }
            assertEquals(emptyList(), ids(store))
            assertEquals(listOf(1L), ids(otherStore))

            runBlocking { apply.apply(applyCommand(sku = Sku("DZ-00000001"), scope = StoreScope.All, sequence = 3)) }
            assertEquals(listOf(1L), ids(store))
        }

        @Test
        fun `삭제되면 사라진다`() {
            seedProduct(1, status = CatalogProductStatus.DRAFT)
            runBlocking { apply.apply(applyCommand(sku = Sku("DZ-00000001"), status = CatalogProductStatus.ACTIVE, sequence = 2)) }
            assertEquals(listOf(1L), ids())

            runBlocking { delete.markDeleted(deleteCommand(productId = ProductId(1))) }

            assertEquals(emptyList(), ids())
        }

        @Test
        fun `처음 복제된 활성 상품은 바로 나타난다`() {
            runBlocking { apply.apply(applyCommand(productId = ProductId(7), sku = Sku("DZ-00000007"), scope = stores(10), sequence = 0)) }

            assertEquals(listOf(7L), ids())
        }
    }
}
