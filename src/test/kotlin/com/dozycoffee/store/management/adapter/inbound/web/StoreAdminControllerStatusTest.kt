package com.dozycoffee.store.management.adapter.inbound.web

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.auth.test.DozyTestTokens
import com.dozycoffee.store.common.time.BusinessCalendar
import com.dozycoffee.store.fixture.insertStoreSql
import com.dozycoffee.store.support.ApiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import tools.jackson.databind.json.JsonMapper
import java.util.stream.Stream
import kotlin.test.assertEquals

@DisplayName("StoreAdminController 상태 전환")
class StoreAdminControllerStatusTest : ApiTest() {
    @Autowired
    private lateinit var tokens: DozyTestTokens

    @Autowired
    private lateinit var calendar: BusinessCalendar

    private fun token(
        type: PrincipalType = PrincipalType.EMPLOYEE,
        vararg roles: String,
    ) = tokens.issue(type = type, roles = roles.toList())

    private fun admin() = token(PrincipalType.EMPLOYEE, "store:admin")

    private fun post(
        action: String,
        path: String = "/1",
        token: String? = admin(),
        ifMatch: String? = null,
        body: String? = null,
    ): WebTestClient.ResponseSpec {
        var request = webTestClient.post().uri("/api/v1/admin/stores$path/$action")
        if (token != null) request = request.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        if (ifMatch != null) request = request.header(HttpHeaders.IF_MATCH, ifMatch)
        return if (body != null) request.contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange() else request.exchange()
    }

    private fun getStore() =
        webTestClient
            .get()
            .uri("/api/v1/admin/stores/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${token(PrincipalType.EMPLOYEE, "store:viewer")}")
            .exchange()

    private fun execute(sql: String) {
        runBlocking {
            databaseClient
                .sql(sql)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    private fun seed(status: String) {
        execute(insertStoreSql("ST-00000001", "강남역점", status, closingDate = "2027-03-31".takeIf { status == "CLOSED" }))
    }

    private fun WebTestClient.ResponseSpec.assertProblem(
        status: Int,
        code: String,
    ) {
        expectStatus()
            .isEqualTo(status)
            .expectHeader()
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo(code)
            .jsonPath("$.traceId")
            .isNotEmpty
    }

    private fun assertStored(
        status: String,
        version: Int,
    ) {
        getStore()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(status)
            .jsonPath("$.version")
            .isEqualTo(version)
    }

    @Nested
    @DisplayName("전환 성공")
    inner class Success {
        @Test
        fun `개점하면 영업 중이 되고 개점일이 오늘로 바뀌며 새 ETag를 돌려준다`() {
            seed("PREPARING")
            val today = calendar.today().toString()

            val result =
                post("open")
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.status")
                    .isEqualTo("OPEN")
                    .jsonPath("$.openingDate")
                    .isEqualTo(today)
                    .jsonPath("$.closingDate")
                    .isEqualTo(null)
                    .jsonPath("$.version")
                    .isEqualTo(1)
                    .returnResult()

            assertEquals("\"1\"", result.responseHeaders.eTag)
            assertStored("OPEN", 1)
        }

        @Test
        fun `휴업하면 휴업 상태가 되고 개점일은 그대로다`() {
            seed("OPEN")

            post("suspend")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("SUSPENDED")
                .jsonPath("$.openingDate")
                .isEqualTo("2026-11-01")
                .jsonPath("$.version")
                .isEqualTo(1)
            assertStored("SUSPENDED", 1)
        }

        @Test
        fun `재개하면 영업 중이 되고 개점일은 그대로다`() {
            seed("SUSPENDED")

            post("resume")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("OPEN")
                .jsonPath("$.openingDate")
                .isEqualTo("2026-11-01")
            assertStored("OPEN", 1)
        }

        @ParameterizedTest
        @ValueSource(strings = ["PREPARING", "OPEN", "SUSPENDED"])
        fun `폐점하면 폐점 상태가 되고 폐점일이 오늘로 기록된다`(from: String) {
            seed(from)
            val today = calendar.today().toString()

            post("close")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("CLOSED")
                .jsonPath("$.closingDate")
                .isEqualTo(today)
                .jsonPath("$.openingDate")
                .isEqualTo("2026-11-01")
            assertStored("CLOSED", 1)
        }

        @Test
        fun `개점 준비부터 폐점까지 이어서 전환할 수 있고 버전은 전환마다 오른다`() {
            seed("PREPARING")

            post("open")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.version")
                .isEqualTo(1)
            post("suspend")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.version")
                .isEqualTo(2)
            post("resume")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.version")
                .isEqualTo(3)
            post("close")
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.version")
                .isEqualTo(4)

            assertStored("CLOSED", 4)
        }

        @Test
        fun `응답의 ETag로 이어서 정보를 수정할 수 있다`() {
            seed("PREPARING")
            val etag =
                post("open")
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .returnResult()
                    .responseHeaders.eTag!!

            webTestClient
                .put()
                .uri("/api/v1/admin/stores/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin()}")
                .header(HttpHeaders.IF_MATCH, etag)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    JsonMapper().writeValueAsString(
                        mapOf(
                            "name" to "강남역점",
                            "address" to
                                mapOf(
                                    "roadAddress" to "도로명",
                                    "postalCode" to "06232",
                                    "sido" to "서울특별시",
                                    "sigungu" to "강남구",
                                ),
                            "phone" to "02-1234-5678",
                            "openingDate" to calendar.today().toString(),
                        ),
                    ),
                ).exchange()
                .expectStatus()
                .isOk
        }

        @Test
        fun `If-Match와 본문을 보내도 무시한다`() {
            seed("PREPARING")

            post("open", ifMatch = "\"99\"", body = """{"무엇": "이든"}""").expectStatus().isOk
            assertStored("OPEN", 1)
        }
    }

    @Nested
    @DisplayName("규칙 위반")
    inner class Rules {
        @ParameterizedTest(name = "{0}의 {1} → {2}")
        @MethodSource("com.dozycoffee.store.management.adapter.inbound.web.StoreAdminControllerStatusTest#allTransitions")
        fun `허용된 전환만 성공하고 나머지는 409 INVALID_STORE_STATUS_TRANSITION이며 저장된 상태와 버전은 그대로다`(
            from: String,
            action: String,
            allowed: Boolean,
        ) {
            seed(from)

            if (allowed) {
                post(action).expectStatus().isOk
            } else {
                post(action).assertProblem(409, "INVALID_STORE_STATUS_TRANSITION")
                assertStored(from, 0)
            }
        }

        @Test
        fun `폐점한 매장은 다시 열 수 없다`() {
            seed("CLOSED")

            listOf("open", "suspend", "resume", "close").forEach { post(it).assertProblem(409, "INVALID_STORE_STATUS_TRANSITION") }

            assertStored("CLOSED", 0)
        }

        @Test
        fun `없는 매장은 404 STORE_NOT_FOUND다`() {
            post("open", path = "/999").assertProblem(404, "STORE_NOT_FOUND")
        }

        @Test
        fun `전환한 뒤 그 전에 본 버전으로 정보를 수정하면 409 VERSION_CONFLICT다`() {
            seed("PREPARING")
            post("open").expectStatus().isOk

            webTestClient
                .put()
                .uri("/api/v1/admin/stores/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${admin()}")
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    JsonMapper().writeValueAsString(
                        mapOf(
                            "name" to "서초점",
                            "address" to
                                mapOf(
                                    "roadAddress" to "도로명",
                                    "postalCode" to "06232",
                                    "sido" to "서울특별시",
                                    "sigungu" to "강남구",
                                ),
                            "phone" to "02-1234-5678",
                            "openingDate" to "2026-11-01",
                        ),
                    ),
                ).exchange()
                .assertProblem(409, "VERSION_CONFLICT")
        }

        @Test
        fun `같은 매장의 개점이 동시에 와도 한 번만 전이된다`() {
            seed("PREPARING")
            val attempts = 6

            val statuses =
                runBlocking {
                    (1..attempts)
                        .map {
                            async(
                                Dispatchers.Default,
                            ) { post("open").returnResult(String::class.java).status.value() }
                        }.awaitAll()
                }

            assertEquals(1, statuses.count { it == 200 })
            assertEquals(attempts - 1, statuses.count { it == 409 })
            assertStored("OPEN", 1)
        }
    }

    @Nested
    @DisplayName("요청 값 검증")
    inner class Validation {
        @ParameterizedTest
        @ValueSource(strings = ["abc", "0", "-1", "1.5"])
        fun `매장 ID가 양의 정수가 아니면 400이다`(value: String) {
            listOf("open", "suspend", "resume", "close").forEach { action ->
                post(action, path = "/$value")
                    .expectStatus()
                    .isBadRequest
                    .expectBody()
                    .jsonPath("$.detail")
                    .isEqualTo("요청 값이 올바르지 않습니다: storeId")
            }
        }
    }

    @Nested
    @DisplayName("인가")
    inner class Authorization {
        private val actions = listOf("open", "suspend", "resume", "close")

        @Test
        fun `매장 관리 권한을 가진 본사 직원은 전환할 수 있다`() {
            seed("PREPARING")

            post("open", token = token(PrincipalType.EMPLOYEE, "store:admin")).expectStatus().isOk
        }

        @Test
        fun `본사 조회, 매장 매니저 역할만, 역할 없음, 다른 서비스 역할은 403이고 전환하지 않는다`() {
            seed("OPEN")

            actions.forEach { action ->
                listOf(
                    token(PrincipalType.EMPLOYEE, "store:viewer"),
                    token(PrincipalType.EMPLOYEE, "store:manager"),
                    token(PrincipalType.EMPLOYEE),
                    token(PrincipalType.EMPLOYEE, "wms:admin"),
                ).forEach { post(action, token = it).assertProblem(403, "FORBIDDEN") }
            }

            assertStored("OPEN", 0)
        }

        @Test
        fun `파트너와 서비스 계정은 403이다`() {
            seed("OPEN")

            actions.forEach { action ->
                post(action, token = token(PrincipalType.PARTNER)).expectStatus().isForbidden
                post(action, token = token(PrincipalType.SYSTEM, "store:admin")).expectStatus().isForbidden
            }
        }

        @Test
        fun `토큰이 없으면 401이다`() {
            seed("OPEN")

            actions.forEach { post(it, token = null).assertProblem(401, "UNAUTHENTICATED") }
        }

        @Test
        fun `인가 거부는 매장 ID 검증보다 먼저다`() {
            actions.forEach {
                post(
                    it,
                    path = "/abc",
                    token = token(PrincipalType.EMPLOYEE, "store:viewer"),
                ).assertProblem(403, "FORBIDDEN")
            }
        }
    }

    companion object {
        /** 동작마다 허용되는 출발 상태(요구사항 1.2). */
        private val allowedFrom =
            mapOf(
                "open" to setOf("PREPARING"),
                "suspend" to setOf("OPEN"),
                "resume" to setOf("SUSPENDED"),
                "close" to setOf("PREPARING", "OPEN", "SUSPENDED"),
            )

        @JvmStatic
        fun allTransitions(): Stream<Arguments> =
            allowedFrom.entries
                .flatMap { (action, allowed) ->
                    listOf("PREPARING", "OPEN", "SUSPENDED", "CLOSED").map { from -> Arguments.of(from, action, from in allowed) }
                }.stream()
    }
}
