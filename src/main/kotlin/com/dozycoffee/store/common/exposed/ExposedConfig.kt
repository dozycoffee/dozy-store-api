package com.dozycoffee.store.common.exposed

import com.dozycoffee.store.common.TransactionRunner
import io.r2dbc.spi.ConnectionFactory
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
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
                    org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
                        .Builder()
                        .apply { defaultMaxAttempts = 1 },
            ).also { database ->
                database.vendor
                database.version
                database.dialect
                database.dialectMetadata
            }

    @Bean
    fun transactionRunner(database: R2dbcDatabase): TransactionRunner = ExposedTransactionRunner(database)
}
