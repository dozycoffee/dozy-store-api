package com.dozycoffee.store.management.adapter.inbound.web

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.auth.test.DozyTestTokens
import com.dozycoffee.store.fixture.insertManagerSql
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
import kotlin.test.assertFalse
import kotlin.test.assertNull

@DisplayName("StoreManagerViewController")
class StoreManagerViewControllerTest : ApiTest() {
    @Autowired
    private lateinit var tokens: DozyTestTokens

    private val json = JsonMapper()

    private val partnerId = UUID.fromString("00000000-0000-7000-8000-00000000000a")
    private val employeeId = UUID.fromString("00000000-0000-7000-8000-00000000000b")

    private fun token(
        type: PrincipalType,
        id: UUID,
        vararg roles: String,
    ) = tokens.issue(type = type, id = id, roles = roles.toList())

    private fun partner(id: UUID = partnerId) = token(PrincipalType.PARTNER, id)

    private fun employee(vararg roles: String) = token(PrincipalType.EMPLOYEE, employeeId, *roles)

    private fun execute(sql: String) {
        runBlocking {
            databaseClient
                .sql(sql)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    /** 1 개점 준비, 2 영업 중, 3 폐점 */
    @BeforeEach
    fun stores() {
        execute(insertStoreSql("ST-00000001", "강남역점", "PREPARING"))
        execute(insertStoreSql("ST-00000002", "역삼점", "OPEN"))
        execute(insertStoreSql("ST-00000003", "폐점점", "CLOSED", closingDate = "2027-03-31"))
    }

    private fun assignPartner(
        storeId: Long,
        id: UUID = partnerId,
    ) = execute(insertManagerSql(storeId, "PARTNER", id))

    private fun assignEmployee(
        storeId: Long,
        id: UUID = employeeId,
    ) = execute(insertManagerSql(storeId, "EMPLOYEE", id))

    private fun get(
        storeId: String,
        token: String?,
    ): WebTestClient.ResponseSpec {
        val request = webTestClient.get().uri("/api/v1/stores/$storeId")
        return (if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer $token") else request).exchange()
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
    @DisplayName("S3 기본 흐름 — 점주(파트너)")
    inner class PartnerManager {
        @Test
        fun `지정된 매장은 version 없이 매장 정보를 돌려준다`() {
            assignPartner(2)

            val result =
                get("2", partner())
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .returnResult()
            val body = json.readTree(result.responseBody!!)

            assertEquals(2, body.get("storeId").asLong())
            assertEquals("ST-00000002", body.get("code").asString())
            assertEquals("역삼점", body.get("name").asString())
            assertEquals("OPEN", body.get("status").asString())
            assertEquals("도로명", body.get("address").get("roadAddress").asString())
            assertEquals("서울특별시", body.get("address").get("sido").asString())
            assertEquals("02-1234-5678", body.get("phone").asString())
            assertEquals("2026-11-01", body.get("openingDate").asString())
            assertFalse(body.has("version"))
            assertNull(result.responseHeaders.getETag())
        }

        @Test
        fun `지정되지 않은 매장은 404다`() {
            assignPartner(2)

            get("1", partner()).assertProblem(404, "STORE_NOT_FOUND")
        }

        @Test
        fun `지정이 하나도 없으면 모든 매장이 404다`() {
            get("1", partner()).assertProblem(404, "STORE_NOT_FOUND")
            get("2", partner()).assertProblem(404, "STORE_NOT_FOUND")
        }

        @Test
        fun `다른 점주의 지정은 내 것이 아니다`() {
            assignPartner(2, UUID.fromString("00000000-0000-7000-8000-0000000000ff"))

            get("2", partner()).assertProblem(404, "STORE_NOT_FOUND")
        }

        @Test
        fun `여러 매장의 매니저면 지정된 매장을 각각 조회한다`() {
            assignPartner(1)
            assignPartner(2)

            get("1", partner()).expectStatus().isOk
            get("2", partner()).expectStatus().isOk
        }

        @Test
        fun `폐점한 매장도 읽을 수 있고 폐점일이 보인다`() {
            assignPartner(3)

            get("3", partner())
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("CLOSED")
                .jsonPath("$.closingDate")
                .isEqualTo("2027-03-31")
        }
    }

    @Nested
    @DisplayName("S3 기본 흐름 — 본사 직원 매니저")
    inner class EmployeeManager {
        @Test
        fun `매장 매니저 역할이 있고 지정된 매장은 읽을 수 있다`() {
            assignEmployee(2)

            get("2", employee("store:manager")).expectStatus().isOk
        }

        @Test
        fun `매장 매니저 역할이 있어도 지정되지 않은 매장은 404다`() {
            assignEmployee(2)

            get("1", employee("store:manager")).assertProblem(404, "STORE_NOT_FOUND")
        }

        @Test
        fun `S3 4c 지정돼 있어도 매장 매니저 역할이 없으면 403이다`() {
            assignEmployee(2)

            get("2", employee()).assertProblem(403, "FORBIDDEN")
        }
    }

    @Nested
    @DisplayName("S3 예외 흐름 4b — 계정 종류")
    inner class PrincipalTypeMismatch {
        @Test
        fun `ID가 같아도 계정 종류가 지정과 다르면 매니저가 아니다 (파트너로 지정된 ID를 직원이 씀)`() {
            assignPartner(2, employeeId)

            get("2", employee("store:manager")).assertProblem(404, "STORE_NOT_FOUND")
        }

        @Test
        fun `ID가 같아도 계정 종류가 지정과 다르면 매니저가 아니다 (직원으로 지정된 ID를 점주가 씀)`() {
            assignEmployee(2, partnerId)

            get("2", partner()).assertProblem(404, "STORE_NOT_FOUND")
        }
    }

    @Nested
    @DisplayName("본사 권한")
    inner class Headquarters {
        @ParameterizedTest
        @ValueSource(strings = ["store:admin", "store:viewer"])
        fun `매니저 지정이 없어도 모든 매장을 읽을 수 있다 (폐점 포함)`(role: String) {
            listOf("1", "2", "3").forEach { get(it, employee(role)).expectStatus().isOk }
        }
    }

    @Nested
    @DisplayName("없는 매장과 접근할 수 없는 매장")
    inner class NotFound {
        @Test
        fun `응답이 같다 (상태, code, detail)`() {
            assignPartner(2)
            val missing =
                get("999", partner())
                    .expectStatus()
                    .isNotFound
                    .expectBody()
                    .returnResult()
            val forbiddenStore =
                get("1", partner())
                    .expectStatus()
                    .isNotFound
                    .expectBody()
                    .returnResult()

            val a = json.readTree(missing.responseBody!!)
            val b = json.readTree(forbiddenStore.responseBody!!)
            assertEquals(a.get("code"), b.get("code"))
            assertEquals(a.get("title"), b.get("title"))
        }

        @Test
        fun `본사 권한이 있어도 없는 매장은 404다`() {
            get("999", employee("store:admin")).assertProblem(404, "STORE_NOT_FOUND")
        }
    }

    @Nested
    @DisplayName("요청 오류와 인가")
    inner class Rejections {
        @ParameterizedTest
        @ValueSource(strings = ["abc", "0", "-1", "1.5"])
        fun `storeId 형식이 틀리면 400이다`(value: String) {
            get(value, partner())
                .expectStatus()
                .isBadRequest
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INVALID_REQUEST")
                .jsonPath("$.detail")
                .isEqualTo("요청 값이 올바르지 않습니다: storeId")
        }

        @Test
        fun `토큰이 없으면 401이다`() {
            get("2", null).assertProblem(401, "UNAUTHENTICATED")
        }

        @Test
        fun `Store 역할이 없는 직원은 403이다`() {
            get("2", employee()).assertProblem(403, "FORBIDDEN")
            get("2", employee("catalog:admin")).assertProblem(403, "FORBIDDEN")
        }

        @Test
        fun `서비스 계정은 403이다`() {
            get("2", token(PrincipalType.SYSTEM, employeeId, "store:catalog_agent")).assertProblem(403, "FORBIDDEN")
        }

        @Test
        fun `인가 거부가 storeId 검증보다 먼저다`() {
            get("abc", employee()).assertProblem(403, "FORBIDDEN")
            get("abc", null).assertProblem(401, "UNAUTHENTICATED")
        }
    }

    private fun mine(token: String?): WebTestClient.ResponseSpec {
        val request = webTestClient.get().uri("/api/v1/me/stores")
        return (if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer $token") else request).exchange()
    }

    private fun namesOf(token: String): List<String> {
        val body =
            mine(token)
                .expectStatus()
                .isOk
                .expectBody()
                .returnResult()
                .responseBody!!
        return json.readTree(body).values().map { it.get("name").asString() }
    }

    @Nested
    @DisplayName("GET /me/stores — 내 매장 목록")
    inner class MyStores {
        @Test
        fun `지정된 매장의 storeId, code, name, status만 돌려준다`() {
            assignPartner(2)

            val body =
                json.readTree(
                    mine(partner())
                        .expectStatus()
                        .isOk
                        .expectBody()
                        .returnResult()
                        .responseBody!!,
                )

            assertEquals(1, body.size())
            val store = body.get(0)
            assertEquals(setOf("storeId", "code", "name", "status"), store.propertyNames().toSet())
            assertEquals(2, store.get("storeId").asLong())
            assertEquals("ST-00000002", store.get("code").asString())
            assertEquals("역삼점", store.get("name").asString())
            assertEquals("OPEN", store.get("status").asString())
        }

        @Test
        fun `지정이 없으면 빈 배열이다`() {
            mine(partner())
                .expectStatus()
                .isOk
                .expectBody()
                .json("[]")
        }

        @Test
        fun `폐점한 매장도 포함하고 폐점하지 않은 매장이 앞에 매장명 가나다순이다`() {
            assignPartner(3)
            assignPartner(2)
            assignPartner(1)

            assertEquals(listOf("강남역점", "역삼점", "폐점점"), namesOf(partner()))
        }

        @Test
        fun `다른 점주의 지정은 보이지 않는다`() {
            assignPartner(1, UUID.fromString("00000000-0000-7000-8000-0000000000ff"))
            assignPartner(2)

            assertEquals(listOf("역삼점"), namesOf(partner()))
        }

        @Test
        fun `매장 매니저 역할이 있는 직원은 자기 지정만 본다`() {
            assignEmployee(1)
            assignPartner(2)

            assertEquals(listOf("강남역점"), namesOf(employee("store:manager")))
        }

        @Test
        fun `본사 권한도 가진 직원은 모든 매장이 아니라 자기가 지정된 매장만 본다`() {
            assignEmployee(2)

            assertEquals(listOf("역삼점"), namesOf(employee("store:manager", "store:admin")))
        }

        @Test
        fun `ID가 같아도 계정 종류가 지정과 다르면 보이지 않는다`() {
            assignPartner(2, employeeId)
            assignEmployee(1, partnerId)

            mine(employee("store:manager"))
                .expectStatus()
                .isOk
                .expectBody()
                .json("[]")
            mine(partner())
                .expectStatus()
                .isOk
                .expectBody()
                .json("[]")
        }

        @ParameterizedTest
        @ValueSource(strings = ["store:admin", "store:viewer"])
        fun `매장 매니저 역할이 없는 직원은 지정돼 있어도 403이다`(role: String) {
            assignEmployee(2)

            mine(employee(role)).assertProblem(403, "FORBIDDEN")
            mine(employee()).assertProblem(403, "FORBIDDEN")
        }

        @Test
        fun `서비스 계정은 403이다`() {
            mine(token(PrincipalType.SYSTEM, employeeId, "store:catalog_agent")).assertProblem(403, "FORBIDDEN")
        }

        @Test
        fun `토큰이 없으면 401이다`() {
            mine(null).assertProblem(401, "UNAUTHENTICATED")
        }
    }
}
