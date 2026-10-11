package com.dozycoffee.store

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = SharedPostgres.container

    /**
     * 컨테이너는 JVM 전체에서 하나만 띄워 모든 테스트 컨텍스트가 공유한다.
     * 로컬 compose.yaml과 같은 버전을 쓴다.
     */
    object SharedPostgres {
        val container: PostgreSQLContainer by lazy {
            PostgreSQLContainer(DockerImageName.parse("postgres:18")).also { it.start() }
        }
    }
}
