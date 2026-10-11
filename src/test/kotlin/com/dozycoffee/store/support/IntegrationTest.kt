package com.dozycoffee.store.support

import com.dozycoffee.store.TestcontainersConfiguration
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.r2dbc.core.DatabaseClient

/**
 * 실제 앱과 같이 Flyway가 만든 스키마 위에서 도는 통합 테스트의 뿌리.
 * `integration` 태그가 붙어 `./gradlew unitTest`에서는 빠진다.
 * 테스트마다 모든 테이블을 비우므로 다른 테스트가 남긴 데이터에 기대지 않는다.
 */
@Tag("integration")
@Import(TestcontainersConfiguration::class)
@SpringBootTest
abstract class IntegrationTest {
    @Autowired
    protected lateinit var databaseClient: DatabaseClient

    @BeforeEach
    fun cleanDatabase() =
        runBlocking {
            val tables =
                databaseClient
                    .sql(
                        """
                        SELECT quote_ident(tablename) AS name FROM pg_tables
                        WHERE schemaname = current_schema() AND tablename <> 'flyway_schema_history'
                        """.trimIndent(),
                    ).map { row -> row.get("name", String::class.java)!! }
                    .all()
                    .collectList()
                    .awaitSingle()
            if (tables.isNotEmpty()) {
                databaseClient
                    .sql("TRUNCATE ${tables.joinToString()} RESTART IDENTITY CASCADE")
                    .fetch()
                    .rowsUpdated()
                    .awaitSingle()
            }
        }
}
