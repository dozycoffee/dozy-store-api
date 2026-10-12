package com.dozycoffee.store.management.application.service

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.store.common.security.StoreRoles
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.fixture.insertManagerSql
import com.dozycoffee.store.fixture.insertStoreSql
import com.dozycoffee.store.fixture.principalKey
import com.dozycoffee.store.management.application.port.inbound.CheckStoreAccessUseCase
import com.dozycoffee.store.management.application.port.inbound.Requester
import com.dozycoffee.store.management.application.port.inbound.StoreAccessMode
import com.dozycoffee.store.management.application.port.inbound.result.StoreStatusResult
import com.dozycoffee.store.management.domain.store.exception.StoreErrorCode
import com.dozycoffee.store.management.domain.store.exception.StoreNotFoundException
import com.dozycoffee.store.support.ApplicationTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("StoreAccessService")
class StoreAccessServiceTest : ApplicationTest() {
    @Autowired
    private lateinit var checkAccess: CheckStoreAccessUseCase

    private val preparing = StoreId(1)
    private val open = StoreId(2)
    private val closed = StoreId(3)

    private val partnerId = UUID(0, 1)
    private val employeeId = UUID(0, 2)

    @BeforeEach
    fun stores() {
        execute(insertStoreSql("ST-00000001", "강남역점", "PREPARING"))
        execute(insertStoreSql("ST-00000002", "역삼점", "OPEN"))
        execute(insertStoreSql("ST-00000003", "폐점점", "CLOSED", closingDate = "2027-03-31"))
    }

    private fun requester(
        type: PrincipalType,
        id: UUID,
        vararg roles: String,
    ) = Requester(principalKey(type, id), roles.toSet())

    private fun partner(id: UUID = partnerId) = requester(PrincipalType.PARTNER, id)

    private fun employee(
        vararg roles: String,
        id: UUID = employeeId,
    ) = requester(PrincipalType.EMPLOYEE, id, *roles)

    private fun check(
        requester: Requester,
        storeId: StoreId,
        mode: StoreAccessMode,
    ) = runBlocking { checkAccess.check(requester, storeId, mode) }

    private fun assertDenied(
        requester: Requester,
        storeId: StoreId,
        mode: StoreAccessMode,
    ) {
        val e = assertFailsWith<StoreNotFoundException> { check(requester, storeId, mode) }
        assertEquals(StoreErrorCode.STORE_NOT_FOUND, e.errorCode)
    }

    @Nested
    @DisplayName("S3 4: 점주 매니저 (파트너 계정)")
    inner class Partner {
        @ParameterizedTest
        @EnumSource(StoreAccessMode::class)
        fun `지정된 매장은 읽고 쓸 수 있다`(mode: StoreAccessMode) {
            execute(insertManagerSql(1, "PARTNER", partnerId))

            val result = check(partner(), preparing, mode)

            assertEquals(preparing, result.storeId)
            assertEquals(StoreStatusResult.PREPARING, result.status)
        }

        @ParameterizedTest
        @EnumSource(StoreAccessMode::class)
        fun `지정되지 않은 다른 매장은 거부한다`(mode: StoreAccessMode) {
            execute(insertManagerSql(1, "PARTNER", partnerId))

            assertDenied(partner(), open, mode)
        }

        @Test
        fun `지정이 하나도 없으면 어떤 매장도 거부한다`() {
            assertDenied(partner(), preparing, StoreAccessMode.READ)
        }

        @Test
        fun `여러 매장의 매니저면 지정된 매장마다 접근할 수 있다`() {
            execute(insertManagerSql(1, "PARTNER", partnerId))
            execute(insertManagerSql(2, "PARTNER", partnerId))

            assertEquals(StoreStatusResult.PREPARING, check(partner(), preparing, StoreAccessMode.WRITE).status)
            assertEquals(StoreStatusResult.OPEN, check(partner(), open, StoreAccessMode.WRITE).status)
        }

        @Test
        fun `폐점한 매장은 지정이 남아 있으면 읽을 수 있고 상태로 폐점을 알려 준다`() {
            execute(insertManagerSql(3, "PARTNER", partnerId))

            assertEquals(StoreStatusResult.CLOSED, check(partner(), closed, StoreAccessMode.READ).status)
        }
    }

    @Nested
    @DisplayName("S3 4a~4c: 본사 직원 매니저와 계정 종류")
    inner class Employee {
        @ParameterizedTest
        @EnumSource(StoreAccessMode::class)
        fun `store manager 역할이 있고 지정된 직원은 읽고 쓸 수 있다`(mode: StoreAccessMode) {
            execute(insertManagerSql(1, "EMPLOYEE", employeeId))

            assertEquals(preparing, check(employee(StoreRoles.MANAGER), preparing, mode).storeId)
        }

        @Test
        fun `store manager 역할이 없으면 매니저로 지정돼 있어도 거부한다 (4c)`() {
            execute(insertManagerSql(1, "EMPLOYEE", employeeId))

            assertDenied(employee(), preparing, StoreAccessMode.READ)
            assertDenied(employee("operator"), preparing, StoreAccessMode.WRITE)
        }

        @Test
        fun `store manager 역할이 있어도 지정되지 않은 매장은 거부한다`() {
            execute(insertManagerSql(1, "EMPLOYEE", employeeId))

            assertDenied(employee(StoreRoles.MANAGER), open, StoreAccessMode.READ)
        }

        @Test
        fun `계정 ID가 같아도 계정 종류가 다르면 매니저가 아니다 (4b)`() {
            execute(insertManagerSql(1, "PARTNER", partnerId))

            assertDenied(requester(PrincipalType.EMPLOYEE, partnerId, StoreRoles.MANAGER), preparing, StoreAccessMode.READ)
        }

        @Test
        fun `직원으로 지정된 ID를 파트너 계정이 쓰는 것도 매니저가 아니다 (4b)`() {
            execute(insertManagerSql(1, "EMPLOYEE", employeeId))

            assertDenied(partner(employeeId), preparing, StoreAccessMode.READ)
        }

        @ParameterizedTest
        @EnumSource(PrincipalType::class, names = ["SYSTEM", "CUSTOMER"])
        fun `서비스 계정과 고객은 매니저로 인정하지 않는다`(type: PrincipalType) {
            execute(insertManagerSql(1, "PARTNER", partnerId))

            assertDenied(requester(type, partnerId, StoreRoles.MANAGER), preparing, StoreAccessMode.READ)
        }
    }

    @Nested
    @DisplayName("본사 직원: 매장 관리·본사 조회 권한")
    inner class Headquarters {
        @ParameterizedTest
        @EnumSource(StoreAccessMode::class)
        fun `매장 관리 권한은 매니저 지정 없이 모든 매장을 읽고 쓸 수 있다 (폐점한 매장 포함)`(mode: StoreAccessMode) {
            val admin = employee(StoreRoles.ADMIN)

            assertEquals(StoreStatusResult.PREPARING, check(admin, preparing, mode).status)
            assertEquals(StoreStatusResult.OPEN, check(admin, open, mode).status)
            assertEquals(StoreStatusResult.CLOSED, check(admin, closed, mode).status)
        }

        @Test
        fun `본사 조회 권한은 모든 매장을 읽을 수 있다`() {
            val viewer = employee(StoreRoles.VIEWER)

            assertEquals(StoreStatusResult.PREPARING, check(viewer, preparing, StoreAccessMode.READ).status)
            assertEquals(StoreStatusResult.CLOSED, check(viewer, closed, StoreAccessMode.READ).status)
        }

        @Test
        fun `본사 조회 권한만으로는 쓸 수 없다`() {
            assertDenied(employee(StoreRoles.VIEWER), preparing, StoreAccessMode.WRITE)
        }

        @Test
        fun `본사 조회 권한이 있어도 그 매장의 매니저로 지정되어 있으면 쓸 수 있다`() {
            execute(insertManagerSql(1, "EMPLOYEE", employeeId))

            val result = check(employee(StoreRoles.VIEWER, StoreRoles.MANAGER), preparing, StoreAccessMode.WRITE)

            assertEquals(preparing, result.storeId)
        }

        @Test
        fun `역할이 없는 직원은 매니저 지정이 없으면 거부한다`() {
            assertDenied(employee(), preparing, StoreAccessMode.READ)
        }
    }

    @Nested
    @DisplayName("없는 매장과 접근할 수 없는 매장")
    inner class NotFound {
        @Test
        fun `매장 관리 권한이어도 없는 매장은 찾을 수 없다`() {
            assertDenied(employee(StoreRoles.ADMIN), StoreId(999), StoreAccessMode.READ)
        }

        @Test
        fun `없는 매장과 지정되지 않은 매장은 같은 오류 코드로 응답한다`() {
            val missing = assertFailsWith<StoreNotFoundException> { check(partner(), StoreId(999), StoreAccessMode.READ) }
            val notAssigned = assertFailsWith<StoreNotFoundException> { check(partner(), open, StoreAccessMode.READ) }

            assertEquals(missing.errorCode, notAssigned.errorCode)
            assertEquals(missing.errorCode.type, notAssigned.errorCode.type)
            assertEquals("매장을 찾을 수 없습니다: 999", missing.message)
            assertEquals("매장을 찾을 수 없습니다: 2", notAssigned.message)
        }
    }
}
