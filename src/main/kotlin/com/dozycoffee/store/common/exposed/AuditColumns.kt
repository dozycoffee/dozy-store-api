package com.dozycoffee.store.common.exposed

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.JavaOffsetDateTimeColumnType
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime

/**
 * 감사 컬럼(`created_at`, `updated_at`). 마이그레이션의 `TIMESTAMPTZ NOT NULL DEFAULT now()`와 같게 정의해 스키마 불일치 검사가 통과하게 한다.
 * 값은 DB 시계로 채운다(docs/erd.md 감사 컬럼 정책). 코드에서는 `Instant`로 바꿔 읽는다.
 */
fun Table.auditTimestamp(name: String): Column<OffsetDateTime> =
    timestampWithTimeZone(name).defaultExpression(CustomFunction("now", JavaOffsetDateTimeColumnType()))
