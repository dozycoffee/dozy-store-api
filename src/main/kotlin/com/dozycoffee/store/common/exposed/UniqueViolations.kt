package com.dozycoffee.store.common.exposed

import io.r2dbc.spi.R2dbcException

private const val UNIQUE_VIOLATION = "23505"

/**
 * 이 예외(또는 원인)가 [constraint]라는 이름의 UNIQUE 제약·인덱스 위반(SQLSTATE 23505)인가.
 * PostgreSQL의 오류 메시지에 제약 이름이 담기므로 그 이름으로 어느 규칙이 깨졌는지 가린다.
 */
fun Throwable.isUniqueViolationOf(constraint: String): Boolean =
    generateSequence(this) { it.cause }
        .filterIsInstance<R2dbcException>()
        .any { it.sqlState == UNIQUE_VIOLATION && it.message.orEmpty().contains("\"$constraint\"") }
