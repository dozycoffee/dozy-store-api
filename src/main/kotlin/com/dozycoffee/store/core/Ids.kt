package com.dozycoffee.store.core

/** Store가 발급한 매장 ID. */
@JvmInline
value class StoreId(
    val value: Long,
) {
    init {
        require(value > 0) { "매장 ID는 양수여야 합니다: $value" }
    }

    override fun toString(): String = value.toString()
}

/** Catalog가 발급한 상품 ID. 등록 순으로 커진다(요구사항 7.1). */
@JvmInline
value class ProductId(
    val value: Long,
) : Comparable<ProductId> {
    init {
        require(value > 0) { "상품 ID는 양수여야 합니다: $value" }
    }

    override fun compareTo(other: ProductId): Int = value.compareTo(other.value)

    override fun toString(): String = value.toString()
}

/** Catalog 상품의 SKU. 실물·사람·다른 회사 시스템이 상품을 가리키는 키. */
@JvmInline
value class Sku(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "SKU는 비어 있을 수 없습니다" }
    }

    override fun toString(): String = value
}
