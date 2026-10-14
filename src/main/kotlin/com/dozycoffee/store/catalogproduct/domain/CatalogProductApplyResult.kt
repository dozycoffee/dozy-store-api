package com.dozycoffee.store.catalogproduct.domain

/**
 * [CatalogProduct.apply]의 결과.
 *
 * @property applied 이벤트를 반영했는가. 오래됐거나 중복된 이벤트, 삭제된 복제본이면 반영하지 않는다.
 * @property scopeChanged 판매 범위가 바뀌었는가. 반영한 경우에만 true일 수 있다.
 */
data class CatalogProductApplyResult(
    val applied: Boolean,
    val scopeChanged: Boolean,
) {
    companion object {
        /** 반영하지 않았다. 복제본은 그대로다. */
        val NOT_APPLIED = CatalogProductApplyResult(applied = false, scopeChanged = false)
    }
}
