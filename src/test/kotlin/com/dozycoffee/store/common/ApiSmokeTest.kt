package com.dozycoffee.store.common

import com.dozycoffee.store.support.ApiTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("API 테스트 기반")
class ApiSmokeTest : ApiTest() {
    @Test
    fun `프로브는 토큰 없이 허용한다`() {
        webTestClient
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `공개 경로가 아닌 요청은 토큰이 없으면 401이다`() {
        webTestClient
            .get()
            .uri("/api/v1/admin/stores")
            .exchange()
            .expectStatus()
            .isUnauthorized
    }
}
