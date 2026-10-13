package com.dozycoffee.store.management.adapter.inbound.web

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.auth.test.DozyTestTokens
import com.dozycoffee.store.fixture.insertManagerSql
import com.dozycoffee.store.fixture.insertStoreSql
import com.dozycoffee.store.support.ApiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@DisplayName("StoreManagerAdminController")
class StoreManagerAdminControllerTest : ApiTest() {
    @Autowired
    private lateinit var tokens: DozyTestTokens

    private val json = JsonMapper()

    private val a = "00000000-0000-7000-8000-00000000000a"
    private val b = "00000000-0000-7000-8000-00000000000b"
    private val c = "00000000-0000-7000-8000-00000000000c"

    private fun token(
        type: PrincipalType = PrincipalType.EMPLOYEE,
        vararg roles: String,
    ) = tokens.issue(type = type, roles = roles.toList())

    private fun admin() = token(PrincipalType.EMPLOYEE, "store:admin")

    private fun viewer() = token(PrincipalType.EMPLOYEE, "store:viewer")

    private fun execute(sql: String) {
        runBlocking {
            databaseClient
                .sql(sql)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    @BeforeEach
    fun stores() {
        execute(insertStoreSql("ST-00000001", "강남역점", "PREPARING"))
        execute(insertStoreSql("ST-00000002", "역삼점", "OPEN"))
        execute(insertStoreSql("ST-00000003", "폐점점", "CLOSED", closingDate = "2027-03-31"))
    }

    private fun bearer(
        request: WebTestClient.RequestHeadersSpec<*>,
        token: String?,
    ) = if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer $token") else request

    private fun assign(
        storeId: String = "1",
        type: String? = "partner",
        id: String? = a,
        token: String? = admin(),
        body: Map<String, Any?>? = null,
    ): WebTestClient.ResponseSpec {
        val payload = body ?: mapOf("principalType" to type, "principalId" to id)
        val request = webTestClient.post().uri("/api/v1/admin/stores/$storeId/managers").contentType(MediaType.APPLICATION_JSON)
        return bearer(request, token).let { (it as WebTestClient.RequestBodySpec).bodyValue(json.writeValueAsString(payload)).exchange() }
    }

    private fun unassign(
        storeId: String = "1",
        key: String = "partner:$a",
        token: String? = admin(),
    ): WebTestClient.ResponseSpec = bearer(webTestClient.delete().uri("/api/v1/admin/stores/$storeId/managers/$key"), token).exchange()

    private fun list(
        storeId: String = "1",
        token: String? = viewer(),
    ): WebTestClient.ResponseSpec = bearer(webTestClient.get().uri("/api/v1/admin/stores/$storeId/managers"), token).exchange()

    private fun byPrincipal(
        query: String,
        token: String? = viewer(),
    ): WebTestClient.ResponseSpec = bearer(webTestClient.get().uri("/api/v1/admin/store-managers$query"), token).exchange()

    private fun managersOf(storeId: String = "1"): List<String> {
        val body =
            list(storeId)
                .expectStatus()
                .isOk
                .expectBody()
                .returnResult()
                .responseBody!!
        return json.readTree(body).values().map { it.get("principalType").asString() + ":" + it.get("principalId").asString() }
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

    private fun WebTestClient.ResponseSpec.assertInvalid(field: String) {
        expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("INVALID_REQUEST")
            .jsonPath("$.detail")
            .isEqualTo("요청 값이 올바르지 않습니다: $field")
    }

    @Nested
    @DisplayName("POST /stores/{storeId}/managers — 매니저 지정")
    inner class Assign {
        @Test
        fun `지정하면 201과 Location, 소문자 계정 종류와 ID를 돌려준다`() {
            val result =
                assign(type = "partner", id = a)
                    .expectStatus()
                    .isCreated
                    .expectBody()
                    .jsonPath("$.storeId")
                    .isEqualTo(1)
                    .jsonPath("$.principalType")
                    .isEqualTo("partner")
                    .jsonPath("$.principalId")
                    .isEqualTo(a)
                    .jsonPath("$.assignedAt")
                    .value<String> { Instant.parse(it) }
                    .returnResult()

            assertEquals("/api/v1/admin/stores/1/managers/partner:$a", result.responseHeaders.location?.path)
            assertEquals(listOf("partner:$a"), managersOf())
        }

        @Test
        fun `본사 직원 계정도 지정할 수 있다`() {
            assign(type = "employee", id = b)
                .expectStatus()
                .isCreated
                .expectBody()
                .jsonPath("$.principalType")
                .isEqualTo("employee")
        }

        @Test
        fun `한 매장에 여러 계정을, 한 계정을 여러 매장에 지정할 수 있다`() {
            assign("1", "partner", a).expectStatus().isCreated
            assign("1", "partner", b).expectStatus().isCreated
            assign("2", "partner", a).expectStatus().isCreated

            assertEquals(listOf("partner:$a", "partner:$b"), managersOf("1"))
            assertEquals(listOf("partner:$a"), managersOf("2"))
        }

        @Test
        fun `계정 ID가 같아도 계정 종류가 다르면 별개의 지정이다`() {
            assign(type = "partner", id = a).expectStatus().isCreated
            assign(type = "employee", id = a).expectStatus().isCreated

            assertEquals(listOf("partner:$a", "employee:$a"), managersOf())
        }

        @Test
        fun `매니저를 교체하려면 해제하고 새로 지정한다`() {
            assign(id = a).expectStatus().isCreated
            unassign(key = "partner:$a").expectStatus().isNoContent
            assign(id = b).expectStatus().isCreated

            assertEquals(listOf("partner:$b"), managersOf())
        }

        @Test
        fun `이미 그 매장의 매니저인 계정은 409 STORE_MANAGER_ALREADY_ASSIGNED다`() {
            assign().expectStatus().isCreated

            assign().assertProblem(409, "STORE_MANAGER_ALREADY_ASSIGNED")

            assertEquals(1, managersOf().size)
        }

        @ParameterizedTest
        @ValueSource(strings = ["system", "customer"])
        fun `매니저로 지정할 수 없는 계정 종류는 400 INVALID_MANAGER_PRINCIPAL_TYPE이다`(type: String) {
            assign(type = type).assertProblem(400, "INVALID_MANAGER_PRINCIPAL_TYPE")

            assertEquals(emptyList(), managersOf())
        }

        @Test
        fun `폐점한 매장에는 409 STORE_CLOSED이고 폐점 전에 지정된 매니저는 남는다`() {
            execute(insertManagerSql(3, "PARTNER", UUID.fromString(a)))

            assign(storeId = "3", id = b).assertProblem(409, "STORE_CLOSED")

            assertEquals(listOf("partner:$a"), managersOf("3"))
        }

        @Test
        fun `없는 매장은 404 STORE_NOT_FOUND다`() {
            assign(storeId = "999").assertProblem(404, "STORE_NOT_FOUND")
        }

        @Test
        fun `같은 계정을 동시에 지정하면 하나만 성공한다`() {
            val statuses =
                runBlocking {
                    (1..6)
                        .map {
                            async(
                                Dispatchers.Default,
                            ) { assign().returnResult(String::class.java).status.value() }
                        }.awaitAll()
                }

            assertEquals(1, statuses.count { it == 201 })
            assertEquals(5, statuses.count { it == 409 })
            assertEquals(1, managersOf().size)
        }

        @Test
        fun `계정 종류와 ID의 형식이 틀리면 필드 이름과 함께 400 INVALID_REQUEST다`() {
            assign(type = "admin").assertInvalid("principalType")
            assign(type = "PARTNER").assertInvalid("principalType")
            assign(type = " partner").assertInvalid("principalType")
            assign(type = null).assertInvalid("principalType")
            assign(id = "abc").assertInvalid("principalId")
            assign(id = a.uppercase()).assertInvalid("principalId")
            assign(id = a.replace("-", "")).assertInvalid("principalId")
            assign(id = null).assertInvalid("principalId")
            assign(body = emptyMap()).assertInvalid("principalType")

            assertEquals(emptyList(), managersOf())
        }

        @ParameterizedTest
        @ValueSource(strings = ["abc", "0", "-1"])
        fun `매장 ID가 양의 정수가 아니면 400이다`(value: String) {
            assign(storeId = value).assertInvalid("storeId")
        }
    }

    @Nested
    @DisplayName("DELETE /stores/{storeId}/managers/{principalKey} — 매니저 해제")
    inner class Unassign {
        @Test
        fun `해제하면 204이고 매니저가 아니게 된다`() {
            assign(id = a).expectStatus().isCreated
            assign(id = b).expectStatus().isCreated

            unassign(key = "partner:$a")
                .expectStatus()
                .isNoContent
                .expectBody()
                .isEmpty

            assertEquals(listOf("partner:$b"), managersOf())
        }

        @Test
        fun `폐점한 매장에서도 해제할 수 있다`() {
            execute(insertManagerSql(3, "PARTNER", UUID.fromString(a)))

            unassign(storeId = "3", key = "partner:$a").expectStatus().isNoContent

            assertEquals(emptyList(), managersOf("3"))
        }

        @Test
        fun `지정이 없으면 404 STORE_MANAGER_NOT_FOUND다`() {
            unassign().assertProblem(404, "STORE_MANAGER_NOT_FOUND")
        }

        @Test
        fun `계정 종류가 다른 키로는 해제되지 않는다`() {
            assign(type = "partner", id = a).expectStatus().isCreated

            unassign(key = "employee:$a").assertProblem(404, "STORE_MANAGER_NOT_FOUND")

            assertEquals(listOf("partner:$a"), managersOf())
        }

        @Test
        fun `다른 매장의 같은 계정은 해제되지 않는다`() {
            assign("1", "partner", a).expectStatus().isCreated
            assign("2", "partner", a).expectStatus().isCreated

            unassign(storeId = "1").expectStatus().isNoContent

            assertEquals(listOf("partner:$a"), managersOf("2"))
        }

        @Test
        fun `저장될 수 없는 계정 종류의 키는 형식은 맞으므로 404다`() {
            unassign(key = "system:$a").assertProblem(404, "STORE_MANAGER_NOT_FOUND")
        }

        @Test
        fun `없는 매장은 404 STORE_NOT_FOUND다`() {
            unassign(storeId = "999").assertProblem(404, "STORE_NOT_FOUND")
        }

        @ParameterizedTest
        @ValueSource(strings = ["abc", "PARTNER:00000000-0000-7000-8000-00000000000a", "partner:abc", "partner:", "partner"])
        fun `principalKey 형식이 틀리면 400 INVALID_REQUEST다`(key: String) {
            unassign(key = key).assertInvalid("principalKey")
        }
    }

    @Nested
    @DisplayName("GET /stores/{storeId}/managers — 매장의 매니저 목록")
    inner class ListByStore {
        @Test
        fun `지정한 순서로 돌려준다`() {
            assign(id = c).expectStatus().isCreated
            assign(id = a).expectStatus().isCreated
            assign(id = b).expectStatus().isCreated

            assertEquals(listOf("partner:$c", "partner:$a", "partner:$b"), managersOf())
        }

        @Test
        fun `매니저가 없으면 빈 배열이다`() {
            list()
                .expectStatus()
                .isOk
                .expectBody()
                .json("[]")
        }

        @Test
        fun `폐점한 매장의 매니저도 조회된다`() {
            execute(insertManagerSql(3, "EMPLOYEE", UUID.fromString(a)))

            assertEquals(listOf("employee:$a"), managersOf("3"))
        }

        @Test
        fun `없는 매장은 404 STORE_NOT_FOUND다`() {
            list("999").assertProblem(404, "STORE_NOT_FOUND")
        }

        @Test
        fun `다른 매장의 매니저는 섞이지 않는다`() {
            assign("1", id = a).expectStatus().isCreated
            assign("2", id = b).expectStatus().isCreated

            assertEquals(listOf("partner:$a"), managersOf("1"))
        }
    }

    @Nested
    @DisplayName("GET /store-managers — 한 계정이 매니저인 지정 목록")
    inner class ListByPrincipal {
        @Test
        fun `폐점한 매장의 지정도 포함해 돌려준다`() {
            assign("1", id = a).expectStatus().isCreated
            execute(insertManagerSql(3, "PARTNER", UUID.fromString(a)))
            assign("2", id = b).expectStatus().isCreated

            val body =
                byPrincipal("?principalKey=partner:$a")
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .returnResult()
                    .responseBody!!

            assertEquals(
                setOf(1, 3),
                json
                    .readTree(body)
                    .values()
                    .map { it.get("storeId").asInt() }
                    .toSet(),
            )
        }

        @Test
        fun `지정이 없으면 빈 배열이다`() {
            byPrincipal("?principalKey=partner:$a")
                .expectStatus()
                .isOk
                .expectBody()
                .json("[]")
        }

        @Test
        fun `계정 종류가 다르면 같은 ID라도 찾지 않는다`() {
            assign(type = "employee", id = a).expectStatus().isCreated

            byPrincipal("?principalKey=partner:$a")
                .expectStatus()
                .isOk
                .expectBody()
                .json("[]")
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "?principalKey=", "?principalKey=abc", "?principalKey=PARTNER:00000000-0000-7000-8000-00000000000a"])
        fun `principalKey가 없거나 형식이 틀리면 400 INVALID_REQUEST다`(query: String) {
            byPrincipal(query).assertInvalid("principalKey")
        }

        @ParameterizedTest
        @ValueSource(strings = ["system", "customer"])
        fun `매니저로 지정할 수 없는 계정 종류는 400 INVALID_REQUEST다`(type: String) {
            byPrincipal("?principalKey=$type:$a").assertInvalid("principalKey")
        }
    }

    @Nested
    @DisplayName("인가")
    inner class Authorization {
        private val noAccess =
            listOf(
                { token(PrincipalType.EMPLOYEE, "store:manager") },
                { token(PrincipalType.EMPLOYEE) },
                { token(PrincipalType.EMPLOYEE, "wms:admin") },
                { token(PrincipalType.PARTNER) },
                { token(PrincipalType.SYSTEM, "store:admin") },
            )

        @Test
        fun `지정과 해제는 매장 관리 권한을 가진 본사 직원만 할 수 있다`() {
            assign(token = token(PrincipalType.EMPLOYEE, "store:admin")).expectStatus().isCreated
            unassign(token = token(PrincipalType.EMPLOYEE, "store:admin")).expectStatus().isNoContent
        }

        @Test
        fun `본사 조회 권한이나 다른 권한으로는 지정과 해제를 할 수 없다`() {
            assign().expectStatus().isCreated

            (noAccess + { viewer() }).forEach { make ->
                assign(id = b, token = make()).assertProblem(403, "FORBIDDEN")
                unassign(token = make()).assertProblem(403, "FORBIDDEN")
            }

            assertEquals(listOf("partner:$a"), managersOf())
        }

        @Test
        fun `조회는 매장 관리와 본사 조회 권한을 가진 본사 직원이 할 수 있다`() {
            listOf(token(PrincipalType.EMPLOYEE, "store:admin"), token(PrincipalType.EMPLOYEE, "store:viewer")).forEach {
                list(token = it).expectStatus().isOk
                byPrincipal("?principalKey=partner:$a", token = it).expectStatus().isOk
            }
        }

        @Test
        fun `조회도 그 밖의 역할과 계정은 403이다`() {
            noAccess.forEach { make ->
                list(token = make()).assertProblem(403, "FORBIDDEN")
                byPrincipal("?principalKey=partner:$a", token = make()).assertProblem(403, "FORBIDDEN")
            }
        }

        @Test
        fun `토큰이 없으면 401이다`() {
            assign(token = null).assertProblem(401, "UNAUTHENTICATED")
            unassign(token = null).assertProblem(401, "UNAUTHENTICATED")
            list(token = null).assertProblem(401, "UNAUTHENTICATED")
            byPrincipal("?principalKey=partner:$a", token = null).assertProblem(401, "UNAUTHENTICATED")
        }

        @Test
        fun `인가 거부는 요청 값 검증보다 먼저다`() {
            val viewer = viewer()

            assign(token = viewer, type = "admin").assertProblem(403, "FORBIDDEN")
            assign(storeId = "abc", token = viewer).assertProblem(403, "FORBIDDEN")
            unassign(key = "abc", token = viewer).assertProblem(403, "FORBIDDEN")
            byPrincipal("", token = token(PrincipalType.PARTNER)).assertProblem(403, "FORBIDDEN")
        }
    }
}
