package com.dozycoffee.store.catalogproduct.domain.exception

import com.dozycoffee.store.core.ErrorCode
import com.dozycoffee.store.core.ErrorType

/** Catalog 상품 복제본(`CatalogProduct`)의 규칙 위반. 상태 코드는 [ErrorType]이 정한다(docs/api/README.md 도메인 오류 코드). */
enum class CatalogProductErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    /**
     * 상품이 없거나, 삭제됐거나, 이 매장이 판매 범위에 들지 않음(요구사항 3.1).
     * 셋을 구분하지 않아 상품 ID로 존재 여부나 판매 범위를 알아낼 수 없게 한다.
     */
    PRODUCT_NOT_FOUND(ErrorType.NOT_FOUND),
    ;

    override val code: String get() = name
}
