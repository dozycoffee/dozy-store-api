package com.dozycoffee.store.catalogproduct.adapter.outbound.persistence

import com.dozycoffee.store.catalogproduct.application.port.outbound.CatalogProductRepository
import com.dozycoffee.store.catalogproduct.domain.CatalogProduct
import com.dozycoffee.store.catalogproduct.domain.StoreScope
import com.dozycoffee.store.common.exposed.dbNow
import com.dozycoffee.store.core.ProductId
import com.dozycoffee.store.core.Sku
import com.dozycoffee.store.core.StoreId
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insertIgnore
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.springframework.stereotype.Repository
import java.time.ZoneOffset

@Repository
class ExposedCatalogProductRepository : CatalogProductRepository {
    override suspend fun findByIdForUpdate(productId: ProductId): CatalogProduct? = find(productId, ForUpdateOption.PostgreSQL.ForUpdate())

    override suspend fun findByIdForShare(productId: ProductId): CatalogProduct? = find(productId, ForUpdateOption.PostgreSQL.ForShare())

    override suspend fun insertIfAbsent(product: CatalogProduct): Boolean {
        val statement =
            CatalogProductsTable.insertIgnore {
                it[this.productId] = product.productId.value
                it[sku] = product.sku?.value
                it[status] = product.status
                it[storeScope] = product.scope.toKind()
                it[tracksInventory] = product.tracksInventory
                it[sequence] = product.sequence
                it[deleted] = product.deleted
                it[lastEventAt] = product.lastEventAt.atOffset(ZoneOffset.UTC)
            }
        if (statement.insertedCount == 0) {
            // ON CONFLICT DO NOTHING은 어느 UNIQUE 위반이든 삼킨다. 같은 상품이 없다면 다른 상품과 SKU가 겹친 것이다.
            check(exists(product.productId)) {
                "상품 ${product.productId}을 넣지 못했습니다. 다른 상품이 같은 SKU(${product.sku})를 쓰고 있습니다 (Catalog의 계약 위반)"
            }
            return false
        }
        insertTargets(product)
        return true
    }

    override suspend fun save(product: CatalogProduct) {
        val updated =
            CatalogProductsTable.update({ CatalogProductsTable.productId eq product.productId.value }) {
                it[sku] = product.sku?.value
                it[status] = product.status
                it[storeScope] = product.scope.toKind()
                it[sequence] = product.sequence
                it[deleted] = product.deleted
                it[lastEventAt] = product.lastEventAt.atOffset(ZoneOffset.UTC)
                it[updatedAt] = dbNow
            }
        check(updated == 1) { "저장된 복제본이 없습니다: ${product.productId}" }
        CatalogProductTargetStoresTable.deleteWhere { CatalogProductTargetStoresTable.productId eq product.productId.value }
        insertTargets(product)
    }

    private suspend fun find(
        productId: ProductId,
        lock: ForUpdateOption,
    ): CatalogProduct? {
        val row =
            CatalogProductsTable
                .selectAll()
                .where { CatalogProductsTable.productId eq productId.value }
                .forUpdate(lock)
                .firstOrNull() ?: return null
        return row.toProduct(targetStoreIds(row))
    }

    /** `LIMITED`일 때만 대상 매장을 읽는다. `ALL`이면 대상 매장 테이블을 읽지 않는다. */
    private suspend fun targetStoreIds(row: ResultRow): Set<StoreId> {
        if (row[CatalogProductsTable.storeScope] != StoreScopeKind.LIMITED) return emptySet()
        return CatalogProductTargetStoresTable
            .select(CatalogProductTargetStoresTable.storeId)
            .where { CatalogProductTargetStoresTable.productId eq row[CatalogProductsTable.productId] }
            .map { StoreId(it[CatalogProductTargetStoresTable.storeId]) }
            .toList()
            .toSet()
    }

    private suspend fun insertTargets(product: CatalogProduct) {
        val scope = product.scope as? StoreScope.Limited ?: return
        if (scope.targetStoreIds.isEmpty()) return
        CatalogProductTargetStoresTable.batchInsert(scope.targetStoreIds.map { it.value }, shouldReturnGeneratedValues = false) { id ->
            this[CatalogProductTargetStoresTable.productId] = product.productId.value
            this[CatalogProductTargetStoresTable.storeId] = id
        }
    }

    private suspend fun exists(productId: ProductId): Boolean =
        CatalogProductsTable
            .select(CatalogProductsTable.productId)
            .where { CatalogProductsTable.productId eq productId.value }
            .firstOrNull() != null

    private fun StoreScope.toKind(): StoreScopeKind =
        when (this) {
            StoreScope.All -> StoreScopeKind.ALL
            is StoreScope.Limited -> StoreScopeKind.LIMITED
        }

    private fun ResultRow.toProduct(targetStoreIds: Set<StoreId>): CatalogProduct =
        CatalogProduct.reconstitute(
            productId = ProductId(this[CatalogProductsTable.productId]),
            sku = this[CatalogProductsTable.sku]?.let { Sku(it) },
            status = this[CatalogProductsTable.status],
            scope =
                when (this[CatalogProductsTable.storeScope]) {
                    StoreScopeKind.ALL -> StoreScope.All
                    StoreScopeKind.LIMITED -> StoreScope.Limited(targetStoreIds)
                },
            tracksInventory = this[CatalogProductsTable.tracksInventory],
            sequence = this[CatalogProductsTable.sequence],
            deleted = this[CatalogProductsTable.deleted],
            lastEventAt = this[CatalogProductsTable.lastEventAt].toInstant(),
        )
}
