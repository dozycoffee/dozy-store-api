package com.dozycoffee.store.common.web

import com.dozycoffee.store.support.ApiTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters

@DisplayName("GlobalExceptionHandler")
@ActiveProfiles(ErrorProbeController.PROFILE)
class GlobalExceptionHandlerTest : ApiTest() {
    private fun get(path: String) = webTestClient.get().uri("/api/v1/public/probe$path").exchange()

    @Nested
    @DisplayName("도메인 예외")
    inner class Domain {
        @ParameterizedTest
        @CsvSource(
            "PROBE_FAILED, 400, Probe failed, probe-failed",
            "PROBE_NOT_FOUND, 404, Probe not found, probe-not-found",
            "PROBE_CONFLICT, 409, Probe conflict, probe-conflict",
            "PROBE_RULE_VIOLATED, 422, Probe rule violated, probe-rule-violated",
        )
        fun `ErrorType으로 상태를 정하고 Problem Details 필드를 모두 담는다`(
            code: String,
            status: Int,
            title: String,
            typeSuffix: String,
        ) {
            get("/domain/$code")
                .expectStatus()
                .isEqualTo(status)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("https://docs.dozycoffee.com/errors/$typeSuffix")
                .jsonPath("$.title")
                .isEqualTo(title)
                .jsonPath("$.status")
                .isEqualTo(status)
                .jsonPath("$.detail")
                .isEqualTo("프로브: $code")
                .jsonPath("$.instance")
                .isEqualTo("/api/v1/public/probe/domain/$code")
                .jsonPath("$.code")
                .isEqualTo(code)
                .jsonPath("$.traceId")
                .isNotEmpty
        }

        @Test
        fun `본문의 traceId는 X-Trace-Id 응답 헤더와 같다`() {
            val result = get("/domain/PROBE_FAILED").returnResult(Map::class.java)

            val header = result.responseHeaders.getFirst("X-Trace-Id")
            assert(header != null && header.length == 32) { "헤더: $header" }
            assert(result.responseBody.blockFirst()!!["traceId"] == header)
        }

        @Test
        fun `요청의 X-Trace-Id가 형식에 맞으면 그대로 쓴다`() {
            webTestClient
                .get()
                .uri("/api/v1/public/probe/domain/PROBE_FAILED")
                .header("X-Trace-Id", "abc-123")
                .exchange()
                .expectHeader()
                .valueEquals("X-Trace-Id", "abc-123")
                .expectBody()
                .jsonPath("$.traceId")
                .isEqualTo("abc-123")
        }

        @Test
        fun `요청의 X-Trace-Id가 형식에 맞지 않으면 새로 만든다`() {
            webTestClient
                .get()
                .uri("/api/v1/public/probe/domain/PROBE_FAILED")
                .header("X-Trace-Id", "not valid!!")
                .exchange()
                .expectBody()
                .jsonPath("$.traceId")
                .value<String> { assert(it.length == 32 && it != "not valid!!") }
        }

        @Test
        fun `버전 충돌은 현재 버전을 확장 필드로 담는다`() {
            get("/version-conflict")
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VERSION_CONFLICT")
                .jsonPath("$.currentVersion")
                .isEqualTo(7)
        }
    }

    @Nested
    @DisplayName("도메인 예외가 아닌 오류")
    inner class NotDomain {
        @Test
        fun `예상하지 못한 예외는 500 INTERNAL_ERROR이고 내부 정보를 담지 않는다`() {
            val body =
                get("/unexpected")
                    .expectStatus()
                    .isEqualTo(500)
                    .expectBody(String::class.java)
                    .returnResult()
                    .responseBody!!

            assert("INTERNAL_ERROR" in body)
            assert("select" !in body && "비밀" !in body && "IllegalStateException" !in body) { body }
        }

        @Test
        fun `읽을 수 없는 본문은 400 INVALID_REQUEST다`() {
            webTestClient
                .post()
                .uri("/api/v1/public/probe/body")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{ 깨진 json"))
                .exchange()
                .expectStatus()
                .isBadRequest
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INVALID_REQUEST")
        }

        @Test
        fun `허용되지 않는 메서드는 상태 이름을 code로 쓴다`() {
            webTestClient
                .delete()
                .uri("/api/v1/public/probe/unexpected")
                .exchange()
                .expectStatus()
                .isEqualTo(405)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("METHOD_NOT_ALLOWED")
        }

        @Test
        fun `없는 경로는 NOT_FOUND다`() {
            webTestClient
                .get()
                .uri("/api/v1/public/probe/없는경로")
                .exchange()
                .expectStatus()
                .isNotFound
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("NOT_FOUND")
        }

        @Test
        fun `컨트롤러에서 던져진 접근 거부는 500이 아니라 403 FORBIDDEN이다`() {
            get("/forbidden")
                .expectStatus()
                .isForbidden
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("FORBIDDEN")
        }
    }

    @Test
    fun `인증 스타터의 401과 같은 형식이다`() {
        val starter: WebTestClient.ResponseSpec = webTestClient.get().uri("/api/v1/admin/stores").exchange()
        starter
            .expectStatus()
            .isUnauthorized
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("UNAUTHENTICATED")
            .jsonPath("$.traceId")
            .isNotEmpty
            .jsonPath("$.instance")
            .isEqualTo("/api/v1/admin/stores")
    }
}
