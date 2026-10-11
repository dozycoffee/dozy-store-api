package com.dozycoffee.store.common.exposed

import com.dozycoffee.store.management.adapter.outbound.persistence.StoresTable
import com.dozycoffee.store.support.ApplicationTest
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.migration.r2dbc.MigrationUtils
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Exposed `Table` 정의와 Flyway가 만든 실제 스키마의 차이를 검사한다(docs/architecture/persistence.md).
 * 새 `Table`을 만들면 [tables]에 등록한다. 그래야 검사 대상이 된다.
 * `WHERE` 조건과 `CHECK` 식은 비교하지 않으므로 Repository 통합 테스트에서 DB에 직접 확인한다.
 */
@DisplayName("스키마 일치")
class SchemaConsistencyTest : ApplicationTest() {
    private val tables: Array<Table> =
        arrayOf(
            StoresTable,
        )

    @Test
    fun `Table 정의와 마이그레이션이 만든 스키마가 같다`() {
        val statements = tx { MigrationUtils.statementsRequiredForDatabaseMigration(*tables) }

        assertTrue(statements.isEmpty(), "스키마와 Table 정의가 어긋납니다:\n${statements.joinToString("\n")}")
    }
}
