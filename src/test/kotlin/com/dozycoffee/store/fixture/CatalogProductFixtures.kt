package com.dozycoffee.store.fixture

import com.dozycoffee.store.catalogproduct.application.port.inbound.command.ApplyCatalogProductSnapshotCommand
import com.dozycoffee.store.catalogproduct.domain.CatalogProduct
import com.dozycoffee.store.catalogproduct.domain.CatalogProductSnapshot
import com.dozycoffee.store.catalogproduct.domain.CatalogProductStatus
import com.dozycoffee.store.catalogproduct.domain.StoreScope
import com.dozycoffee.store.core.ProductId
import com.dozycoffee.store.core.Sku
import java.time.Instant

/** Catalog가 알려 준 상품 상태. 기본값은 유효한 최소 상태(`ACTIVE`, 모든 매장)다. */
fun snapshot(
    productId: ProductId = ProductId(1),
    sku: Sku? = Sku("DZ-00000001"),
    status: CatalogProductStatus = CatalogProductStatus.ACTIVE,
    scope: StoreScope = StoreScope.All,
    tracksInventory: Boolean = false,
    sequence: Long = 1,
    occurredAt: Instant = Instant.parse("2026-10-14T01:00:00Z"),
) = CatalogProductSnapshot(productId, sku, status, scope, tracksInventory, sequence, occurredAt)

/** 저장된 복제본. 기본값은 삭제되지 않은 `ACTIVE` 상품이다. */
fun catalogProduct(
    productId: ProductId = ProductId(1),
    sku: Sku? = Sku("DZ-00000001"),
    status: CatalogProductStatus = CatalogProductStatus.ACTIVE,
    scope: StoreScope = StoreScope.All,
    tracksInventory: Boolean = false,
    sequence: Long = 1,
    deleted: Boolean = false,
    lastEventAt: Instant = Instant.parse("2026-10-14T01:00:00Z"),
) = CatalogProduct.reconstitute(productId, sku, status, scope, tracksInventory, sequence, deleted, lastEventAt)

/** Catalog 이벤트를 번역한 반영 요청. 기본값은 [snapshot]과 같다. */
fun applyCommand(
    productId: ProductId = ProductId(1),
    sku: Sku? = Sku("DZ-00000001"),
    status: CatalogProductStatus = CatalogProductStatus.ACTIVE,
    scope: StoreScope = StoreScope.All,
    tracksInventory: Boolean = false,
    sequence: Long = 1,
    occurredAt: Instant = Instant.parse("2026-10-14T01:00:00Z"),
) = ApplyCatalogProductSnapshotCommand(productId, sku, status, scope, tracksInventory, sequence, occurredAt)
