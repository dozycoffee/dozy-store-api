package com.dozycoffee.store.common.web

import com.dozycoffee.store.support.ApiTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
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

    @Nested
    @DisplayName("INVALID_REQUEST의 필드 이름")
    inner class InvalidRequestField {
        private fun postNested(json: String) =
            webTestClient
                .post()
                .uri("/api/v1/public/probe/body-nested")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(json))
                .exchange()

        private fun WebTestClient.ResponseSpec.assertInvalid(detail: String) {
            expectStatus()
                .isBadRequest
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INVALID_REQUEST")
                .jsonPath("$.detail")
                .isEqualTo(detail)
        }

        @Test
        fun `요청 값 검증 예외는 필드 이름을 detail에 담는다`() {
            get("/invalid-request").assertInvalid("요청 값이 올바르지 않습니다: address.roadAddress")
        }

        @Test
        fun `허용되지 않는 enum 값은 그 필드 이름을 알린다`() {
            postNested("""{"status": "UNKNOWN"}""").assertInvalid("요청 값이 올바르지 않습니다: status")
        }

        @Test
        fun `날짜 형식이 틀리면 그 필드 이름을 알린다`() {
            postNested("""{"openingDate": "2026-13-45"}""").assertInvalid("요청 값이 올바르지 않습니다: openingDate")
        }

        @Test
        fun `중첩된 필드는 점으로 이은 경로를 알린다`() {
            postNested("""{"address": {"roadAddress": {"잘못": "객체"}}}""")
                .assertInvalid("요청 값이 올바르지 않습니다: address.roadAddress")
        }

        @Test
        fun `목록 안의 필드는 위치까지 알린다`() {
            postNested("""{"items": [{"roadAddress": "정상"}, {"roadAddress": {"잘못": "객체"}}]}""")
                .assertInvalid("요청 값이 올바르지 않습니다: items[1].roadAddress")
        }

        @Test
        fun `쿼리 파라미터의 타입이 틀리면 그 파라미터 이름을 알린다`() {
            webTestClient
                .get()
                .uri("/api/v1/public/probe/number?size=abc")
                .exchange()
                .assertInvalid("요청 값이 올바르지 않습니다: size")
        }

        @Test
        fun `입력한 값은 응답에 담지 않는다`() {
            val body =
                postNested("""{"status": "비밀로그인값"}""")
                    .expectStatus()
                    .isBadRequest
                    .expectBody(String::class.java)
                    .returnResult()
                    .responseBody!!

            assert("비밀로그인값" !in body) { body }
        }

        @Test
        fun `문법이 깨진 본문은 필드를 알 수 없어 일반 문구다`() {
            postNested("{ 깨진 json").assertInvalid("요청을 읽을 수 없습니다")
        }
    }

    @Nested
    @DisplayName("If-Match와 428")
    inner class IfMatchHeader {
        private fun put(ifMatch: String?) =
            webTestClient
                .put()
                .uri("/api/v1/public/probe/needs-version")
                .let { if (ifMatch != null) it.header("If-Match", ifMatch) else it }
                .exchange()

        @Test
        fun `헤더가 없으면 428 VERSION_REQUIRED다`() {
            put(null)
                .expectStatus()
                .isEqualTo(428)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VERSION_REQUIRED")
                .jsonPath("$.title")
                .isEqualTo("Version required")
                .jsonPath("$.type")
                .isEqualTo("https://docs.dozycoffee.com/errors/version-required")
                .jsonPath("$.status")
                .isEqualTo(428)
                .jsonPath("$.traceId")
                .isNotEmpty
        }

        @Test
        fun `올바른 헤더는 버전을 읽는다`() {
            put("\"7\"")
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .isEqualTo("7")
        }

        @ParameterizedTest
        @ValueSource(strings = ["7", "W/\"7\"", "*", "\"abc\"", "\"7\", \"8\""])
        fun `형식이 틀리면 400 INVALID_REQUEST이고 If-Match를 알린다`(header: String) {
            put(header)
                .expectStatus()
                .isBadRequest
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INVALID_REQUEST")
                .jsonPath("$.detail")
                .isEqualTo("요청 값이 올바르지 않습니다: If-Match")
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
