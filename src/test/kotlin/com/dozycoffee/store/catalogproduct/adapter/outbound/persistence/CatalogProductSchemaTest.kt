package com.dozycoffee.store.catalogproduct.adapter.outbound.persistence

import com.dozycoffee.store.support.ApplicationTest
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 마이그레이션이 만든 제약과 인덱스가 ERD대로 동작하는지 SQL로 직접 확인한다(docs/architecture/persistence.md). */
@DisplayName("catalog_products 스키마")
class CatalogProductSchemaTest : ApplicationTest() {
    private fun product(
        id: Long = 1,
        sku: String? = "DZ-00000001",
        status: String = "ACTIVE",
        scope: String = "ALL",
    ) = """
        INSERT INTO catalog_products (product_id, sku, status, store_scope, tracks_inventory, sequence, last_event_at)
        VALUES ($id, ${sku?.let { "'$it'" } ?: "NULL"}, '$status', '$scope', false, 1, '2026-10-14T01:00:00Z')
        """.trimIndent()

    private fun target(
        productId: Long,
        storeId: Long,
    ) = "INSERT INTO catalog_product_target_stores (product_id, store_id) VALUES ($productId, $storeId)"

    private fun count(sql: String): Long =
        runBlocking {
            databaseClient
                .sql(sql)
                .map { row -> row.get(0, java.lang.Long::class.java)!!.toLong() }
                .one()
                .awaitSingle()
        }

    /** [constraint]라는 이름의 제약을 어겨 거부됐는가. PostgreSQL의 오류 메시지에 제약 이름이 담긴다. */
    private fun assertViolates(
        constraint: String,
        block: () -> Unit,
    ) {
        val e = assertFailsWith<Exception> { block() }
        val messages = generateSequence<Throwable>(e) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(messages.any { "\"$constraint\"" in it }, "제약 $constraint 위반이 아닙니다: $messages")
    }

    @Nested
    @DisplayName("catalog_products")
    inner class Products {
        @Test
        fun `상품 ID와 값을 저장한다 (identity가 아니라 넣은 값이 그대로다)`() {
            execute(product(id = 1024))

            assertEquals(1, count("SELECT count(*) FROM catalog_products WHERE product_id = 1024"))
        }

        @Test
        fun `감사 컬럼과 삭제 표시는 기본값이 채워진다`() {
            execute(product())

            assertEquals(
                1,
                count("SELECT count(*) FROM catalog_products WHERE deleted = false AND created_at IS NOT NULL AND updated_at IS NOT NULL"),
            )
        }

        @Test
        fun `같은 상품 ID는 중복할 수 없다`() {
            execute(product(id = 1))

            assertViolates("catalog_products_pkey") { execute(product(id = 1, sku = "DZ-00000002")) }
        }

        @Test
        fun `같은 SKU는 중복할 수 없다`() {
            execute(product(id = 1, sku = "DZ-00000001"))

            assertViolates("catalog_products_sku_key") { execute(product(id = 2, sku = "DZ-00000001")) }
        }

        @Test
        fun `SKU가 없는 상품은 여러 개 있을 수 있다`() {
            execute(product(id = 1, sku = null))
            execute(product(id = 2, sku = null))

            assertEquals(2, count("SELECT count(*) FROM catalog_products WHERE sku IS NULL"))
        }

        @ParameterizedTest
        @ValueSource(strings = ["DRAFT", "ACTIVE", "DISCONTINUED"])
        fun `상태는 세 값을 받는다`(status: String) {
            execute(product(status = status))

            assertEquals(1, count("SELECT count(*) FROM catalog_products WHERE status = '$status'"))
        }

        @Test
        fun `모르는 상태는 거부한다`() {
            assertViolates("catalog_products_status_check") { execute(product(status = "UNKNOWN")) }
        }

        @ParameterizedTest
        @ValueSource(strings = ["ALL", "LIMITED"])
        fun `판매 범위는 두 값을 받는다`(scope: String) {
            execute(product(scope = scope))

            assertEquals(1, count("SELECT count(*) FROM catalog_products WHERE store_scope = '$scope'"))
        }

        @Test
        fun `모르는 판매 범위는 거부한다`() {
            assertViolates("catalog_products_store_scope_check") { execute(product(scope = "NONE")) }
        }

        @Test
        fun `부분 인덱스는 삭제된 상품을 제외한다`() {
            val definition =
                runBlocking {
                    databaseClient
                        .sql("SELECT indexdef FROM pg_indexes WHERE indexname = 'catalog_products_status_idx'")
                        .map { row -> row.get(0, String::class.java)!! }
                        .one()
                        .awaitSingle()
                }

            assertTrue("WHERE" in definition && "deleted" in definition, definition)
        }
    }

    @Nested
    @DisplayName("catalog_product_target_stores")
    inner class TargetStores {
        @Test
        fun `상품의 대상 매장을 여러 개 저장한다`() {
            execute(product(scope = "LIMITED"))
            execute(target(1, 10))
            execute(target(1, 20))

            assertEquals(2, count("SELECT count(*) FROM catalog_product_target_stores WHERE product_id = 1"))
        }

        @Test
        fun `같은 상품에 같은 매장은 중복할 수 없다`() {
            execute(product(scope = "LIMITED"))
            execute(target(1, 10))

            assertViolates("catalog_product_target_stores_pkey") { execute(target(1, 10)) }
        }

        @Test
        fun `다른 상품은 같은 매장을 대상으로 할 수 있다`() {
            execute(product(id = 1, sku = "DZ-00000001", scope = "LIMITED"))
            execute(product(id = 2, sku = "DZ-00000002", scope = "LIMITED"))
            execute(target(1, 10))
            execute(target(2, 10))

            assertEquals(2, count("SELECT count(*) FROM catalog_product_target_stores WHERE store_id = 10"))
        }

        @Test
        fun `없는 상품의 대상은 거부한다`() {
            assertViolates("catalog_product_target_stores_product_id_fkey") { execute(target(999, 10)) }
        }

        @Test
        fun `매장 ID에는 FK가 없어 Catalog가 보낸 모르는 매장 ID도 저장한다`() {
            execute(product(scope = "LIMITED"))

            execute(target(1, 987_654_321))

            assertEquals(1, count("SELECT count(*) FROM catalog_product_target_stores WHERE store_id = 987654321"))
        }

        @Test
        fun `상품을 지우면 대상 매장도 함께 지워진다 (ON DELETE CASCADE)`() {
            execute(product(scope = "LIMITED"))
            execute(target(1, 10))
            execute(target(1, 20))

            execute("DELETE FROM catalog_products WHERE product_id = 1")

            assertEquals(0, count("SELECT count(*) FROM catalog_product_target_stores"))
        }
    }
}
