package com.dozycoffee.store.catalogproduct.application.service

import com.dozycoffee.store.catalogproduct.application.port.inbound.ApplyCatalogProductSnapshotUseCase
import com.dozycoffee.store.catalogproduct.application.port.inbound.CheckProductSellableUseCase
import com.dozycoffee.store.catalogproduct.application.port.inbound.MarkCatalogProductDeletedUseCase
import com.dozycoffee.store.catalogproduct.application.port.inbound.command.ApplyCatalogProductSnapshotCommand
import com.dozycoffee.store.catalogproduct.application.port.inbound.command.MarkCatalogProductDeletedCommand
import com.dozycoffee.store.catalogproduct.application.port.inbound.result.ApplyOutcome
import com.dozycoffee.store.catalogproduct.application.port.inbound.result.DeleteOutcome
import com.dozycoffee.store.catalogproduct.application.port.inbound.result.SellableProductResult
import com.dozycoffee.store.catalogproduct.application.port.outbound.CatalogProductRepository
import com.dozycoffee.store.catalogproduct.domain.CatalogProduct
import com.dozycoffee.store.catalogproduct.domain.CatalogProductSnapshot
import com.dozycoffee.store.catalogproduct.domain.exception.ProductNotFoundException
import com.dozycoffee.store.common.TransactionRunner
import com.dozycoffee.store.common.event.DomainEventDispatcher
import com.dozycoffee.store.core.ProductId
import com.dozycoffee.store.core.StoreId
import org.springframework.stereotype.Service

/**
 * Catalog 상품 복제본에 Catalog의 변경을 반영한다. 유스케이스마다 트랜잭션 경계를 열고, 같은 상품은 상품 행을 잠가 직렬화한다.
 * 복제본이 바뀌면 저장한 뒤 같은 트랜잭션에서 도메인 이벤트를 처리한다.
 */
@Service
class CatalogProductService(
    private val products: CatalogProductRepository,
    private val events: DomainEventDispatcher,
    private val transactions: TransactionRunner,
) : ApplyCatalogProductSnapshotUseCase,
    MarkCatalogProductDeletedUseCase,
    CheckProductSellableUseCase {
    override suspend fun apply(command: ApplyCatalogProductSnapshotCommand): ApplyOutcome =
        transactions.inTransaction {
            val snapshot = command.toSnapshot()
            val existing = products.findByIdForUpdate(snapshot.productId)
            if (existing != null) return@inTransaction applyTo(existing, snapshot)

            val replicated = CatalogProduct.replicate(snapshot)
            if (products.insertIfAbsent(replicated)) {
                events.dispatch(replicated.pullDomainEvents())
                return@inTransaction ApplyOutcome.REPLICATED
            }
            // 같은 상품이 처음 들어오는 이벤트를 다른 트랜잭션이 동시에 처리했고, 그쪽이 먼저 행을 넣었다(없다고 읽은 뒤 넣는 사이에 생긴 행).
            // 그냥 끝내면 이 이벤트가 사라지므로, 그 행을 잠그고 일반 경로와 같은 규칙(순번 비교, 교체)으로 반영한다.
            // 복제본은 지우지 않고 삭제 표시만 하므로 다시 읽으면 반드시 있다.
            applyTo(checkNotNull(products.findByIdForUpdate(snapshot.productId)), snapshot)
        }

    override suspend fun markDeleted(command: MarkCatalogProductDeletedCommand): DeleteOutcome =
        transactions.inTransaction {
            val product = products.findByIdForUpdate(command.productId) ?: return@inTransaction DeleteOutcome.UNKNOWN_PRODUCT
            if (!product.markDeleted(command.occurredAt)) return@inTransaction DeleteOutcome.ALREADY_DELETED
            products.save(product)
            events.dispatch(product.pullDomainEvents())
            DeleteOutcome.DELETED
        }

    override suspend fun check(
        storeId: StoreId,
        productId: ProductId,
    ): SellableProductResult =
        transactions.inTransaction {
            sellable(storeId, productId, products.findByIdForShare(productId))
        }

    override suspend fun checkAll(
        storeId: StoreId,
        productIds: Collection<ProductId>,
    ): List<SellableProductResult> =
        transactions.inTransaction {
            val ids = productIds.distinct().sortedBy { it.value }
            val found = products.findByIdsForShare(ids).associateBy { it.productId }
            ids.map { sellable(storeId, it, found[it]) }
        }

    /** 삭제되지 않았고 판매 범위가 이 매장을 포함하면 다룰 수 있다. 없는 상품, 삭제된 상품, 범위 밖의 상품은 구분하지 않고 거부한다. */
    private fun sellable(
        storeId: StoreId,
        productId: ProductId,
        product: CatalogProduct?,
    ): SellableProductResult {
        if (product == null || product.deleted || !product.scope.covers(storeId)) throw ProductNotFoundException(productId)
        return SellableProductResult(product.productId, product.tracksInventory)
    }

    private suspend fun applyTo(
        product: CatalogProduct,
        snapshot: CatalogProductSnapshot,
    ): ApplyOutcome {
        if (!product.apply(snapshot).applied) return ApplyOutcome.IGNORED
        products.save(product)
        events.dispatch(product.pullDomainEvents())
        return ApplyOutcome.APPLIED
    }
}
