package com.dozycoffee.store.management.adapter.inbound.web

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.auth.test.DozyTestTokens
import com.dozycoffee.store.fixture.insertStoreSql
import com.dozycoffee.store.support.ApiTest
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import kotlin.test.assertEquals

@DisplayName("StoreInternalController")
class StoreInternalControllerTest : ApiTest() {
    @Autowired
    private lateinit var tokens: DozyTestTokens

    private val json = JsonMapper()

    private val serviceId = UUID.fromString("00000000-0000-7000-8000-0000000000c1")

    private fun catalog() = tokens.issue(type = PrincipalType.SYSTEM, id = serviceId, roles = listOf("store:catalog_agent"))

    private fun token(
        type: PrincipalType,
        vararg roles: String,
    ) = tokens.issue(type = type, id = serviceId, roles = roles.toList())

    private fun execute(sql: String) {
        runBlocking {
            databaseClient
                .sql(sql)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    /** 1 개점 준비, 2 영업 중, 3 휴업, 4 폐점 */
    @BeforeEach
    fun stores() {
        execute(insertStoreSql("ST-00000001", "강남역점", "PREPARING"))
        execute(insertStoreSql("ST-00000002", "역삼점", "OPEN"))
        execute(insertStoreSql("ST-00000003", "선릉점", "SUSPENDED"))
        execute(insertStoreSql("ST-00000004", "폐점점", "CLOSED", closingDate = "2027-03-31"))
    }

    private fun check(
        body: String,
        token: String? = catalog(),
    ): WebTestClient.ResponseSpec {
        val request = webTestClient.post().uri("/api/v1/internal/stores/assignability-check").contentType(MediaType.APPLICATION_JSON)
        return (if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer $token") else request).bodyValue(body).exchange()
    }

    private fun ids(vararg ids: Long) = """{"storeIds":${ids.toList()}}"""

    private fun unassignable(body: String): List<Pair<Long, String>> {
        val result =
            check(body)
                .expectStatus()
                .isOk
                .expectBody()
                .returnResult()
                .responseBody!!
        return json
            .readTree(result)
            .get("unassignableStores")
            .values()
            .map { it.get("storeId").asLong() to it.get("reason").asString() }
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
    }

    @Nested
    @DisplayName("판단 (요구사항 7.2)")
    inner class Judgement {
        @Test
        fun `없는 매장은 NOT_FOUND, 폐점한 매장은 CLOSED다`() {
            assertEquals(listOf(999L to "NOT_FOUND", 4L to "CLOSED"), unassignable(ids(999, 4)))
        }

        @Test
        fun `개점 준비, 영업 중, 휴업 매장은 지정할 수 있다`() {
            check(ids(1, 2, 3))
                .expectStatus()
                .isOk
                .expectBody()
                .json("""{"unassignableStores":[]}""")
        }

        @Test
        fun `결과는 요청한 ID 순서를 따른다`() {
            assertEquals(listOf(4L to "CLOSED", 999L to "NOT_FOUND", 998L to "NOT_FOUND"), unassignable(ids(4, 1, 999, 2, 998)))
        }

        @Test
        fun `같은 ID가 여러 번 와도 한 번만 판단한다`() {
            assertEquals(listOf(999L to "NOT_FOUND", 4L to "CLOSED"), unassignable(ids(999, 4, 999, 4, 2, 2)))
        }

        @Test
        fun `1000개까지 확인할 수 있다`() {
            val all = (1L..1000L).toList()

            val result = unassignable(ids(*all.toLongArray()))

            assertEquals(997, result.size)
            assertEquals(4L to "CLOSED", result.first())
        }
    }

    @Nested
    @DisplayName("요청 오류 400")
    inner class InvalidBody {
        private fun WebTestClient.ResponseSpec.assertInvalid() {
            expectStatus()
                .isBadRequest
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INVALID_REQUEST")
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "{}",
                """{"storeIds":null}""",
                """{"storeIds":[]}""",
                """{"storeIds":[null]}""",
                """{"storeIds":[1,null]}""",
                """{"storeIds":[0]}""",
                """{"storeIds":[-1]}""",
            ],
        )
        fun `storeIds가 없거나 비었거나 항목이 양의 정수가 아니면 storeIds 필드로 거부한다`(body: String) {
            check(body)
                .expectStatus()
                .isBadRequest
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INVALID_REQUEST")
                .jsonPath("$.detail")
                .isEqualTo("요청 값이 올바르지 않습니다: storeIds")
        }

        @Test
        fun `1001개면 거부하고 중복을 빼기 전의 항목 수로 센다`() {
            val body = ids(*LongArray(1001) { 2L })

            check(body)
                .expectStatus()
                .isBadRequest
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("요청 값이 올바르지 않습니다: storeIds")
        }

        @ParameterizedTest
        @ValueSource(strings = ["""{"storeIds":["abc"]}""", """{"storeIds":"1"}""", "not json", ""])
        fun `숫자가 아니거나 JSON이 아니면 400이다`(body: String) {
            check(body).assertInvalid()
        }
    }

    @Nested
    @DisplayName("인가")
    inner class Authorization {
        @Test
        fun `토큰이 없으면 401이다`() {
            check(ids(1), token = null).assertProblem(401, "UNAUTHENTICATED")
        }

        @Test
        fun `catalog_agent 역할이 없는 서비스 계정은 403이다`() {
            check(ids(1), token(PrincipalType.SYSTEM)).assertProblem(403, "FORBIDDEN")
            check(ids(1), token(PrincipalType.SYSTEM, "catalog:store_agent")).assertProblem(403, "FORBIDDEN")
        }

        @ParameterizedTest
        @ValueSource(strings = ["store:admin", "store:viewer", "store:manager", "store:catalog_agent"])
        fun `본사 직원은 어떤 역할이든 403이다 (catalog_agent를 가져도 서비스 계정이 아니다)`(role: String) {
            check(ids(1), token(PrincipalType.EMPLOYEE, role)).assertProblem(403, "FORBIDDEN")
        }

        @Test
        fun `점주는 403이다`() {
            check(ids(1), token(PrincipalType.PARTNER)).assertProblem(403, "FORBIDDEN")
        }

        @Test
        fun `인가 거부가 요청 값 검증보다 먼저다`() {
            check("{}", token(PrincipalType.EMPLOYEE, "store:admin")).assertProblem(403, "FORBIDDEN")
            check("{}", token = null).assertProblem(401, "UNAUTHENTICATED")
        }

        @ParameterizedTest
        @ValueSource(strings = ["/api/v1/admin/stores", "/api/v1/admin/stores/1", "/api/v1/stores/1", "/api/v1/me/stores"])
        fun `catalog_agent는 다른 경로를 부를 수 없다`(path: String) {
            webTestClient
                .get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${catalog()}")
                .exchange()
                .assertProblem(403, "FORBIDDEN")
        }
    }
}
