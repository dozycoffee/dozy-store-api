package com.dozycoffee.store.common.exposed

import com.dozycoffee.store.common.TransactionRunner
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ExposedConfig {
    /**
     * Boot가 자동 구성한 `ConnectionFactory`(풀 포함)를 Exposed가 감싼다.
     *
     * - 자동 재시도를 끈다. DB 예외가 나면 블록 전체를 다시 실행하는 기본 동작은 동시성 버그를 가리고
     *   블록 안의 외부 호출을 중복시킨다.
     * - 연결 메타데이터는 여기서 미리 읽는다. Exposed는 이를 처음 쓸 때 블로킹으로 DB에 묻는데,
     *   첫 사용이 Netty 이벤트 루프 스레드에서 일어나면 그 스레드가 멈춘다. 빈을 만드는 시작 시점은 이벤트 루프 밖이다.
     */
    @Bean
    fun r2dbcDatabase(connectionFactory: ConnectionFactory): R2dbcDatabase =
        R2dbcDatabase
            .connect(
                connectionFactory,
                databaseConfig =
                    R2dbcDatabaseConfig.Builder().apply {
                        // 풀이 감싼 ConnectionFactory에서는 드라이버를 알 수 없으므로 방언을 직접 지정한다. Store는 PostgreSQL만 쓴다.
                        explicitDialect = PostgreSQLDialect()
                        defaultMaxAttempts = 1
                    },
            ).also(::preloadMetadata)

    /** 메타데이터 조회도 트랜잭션 안에서 해야 하므로 시작할 때 트랜잭션을 하나 열어 lazy 값을 모두 읽어 둔다. */
    private fun preloadMetadata(database: R2dbcDatabase) {
        runBlocking {
            suspendTransaction(database) {
                database.vendor
                database.dialect
                database.dialectMetadata
                database.dialectMode
                database.version
                database.fullVersion
                database.identifierManager
            }
        }
    }

    @Bean
    fun transactionRunner(database: R2dbcDatabase): TransactionRunner = ExposedTransactionRunner(database)
}
