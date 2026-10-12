package com.dozycoffee.store.management.adapter.inbound.web.request

import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.store.common.web.InvalidRequestException
import com.dozycoffee.store.core.StoreId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("AssignStoreManagerRequest")
class AssignStoreManagerRequestTest {
    private val id = "0199a3c4-7b2e-7c1a-9f3d-2b6e8a1c4d5f"

    @Test
    fun `매장 ID와 principal key를 담은 Command로 바꾼다`() {
        val command = AssignStoreManagerRequest("partner", id).toCommand(StoreId(101))

        assertEquals(StoreId(101), command.storeId)
        assertEquals(PrincipalType.PARTNER, command.principal.type)
        assertEquals(UUID.fromString(id), command.principal.id)
    }

    @Test
    fun `잘못된 값은 필드 이름과 함께 거부한다`() {
        assertEquals(
            "principalType",
            assertFailsWith<InvalidRequestException> {
                AssignStoreManagerRequest("admin", id).toCommand(StoreId(1))
            }.field,
        )
        assertEquals(
            "principalId",
            assertFailsWith<InvalidRequestException> {
                AssignStoreManagerRequest("partner", "abc").toCommand(StoreId(1))
            }.field,
        )
        assertEquals(
            "principalType",
            assertFailsWith<InvalidRequestException> {
                AssignStoreManagerRequest(null, null).toCommand(StoreId(1))
            }.field,
        )
    }
}
