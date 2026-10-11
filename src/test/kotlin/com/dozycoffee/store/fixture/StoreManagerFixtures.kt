package com.dozycoffee.store.fixture

import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.store.core.StoreId
import com.dozycoffee.store.management.domain.manager.NewStoreManagerAssignment
import com.dozycoffee.store.management.domain.manager.StoreManagerAssignment
import java.time.Instant
import java.util.UUID

val PARTNER_ID: UUID = UUID.fromString("0199a3c4-0000-7000-8000-000000000001")

fun principalKey(
    type: PrincipalType = PrincipalType.PARTNER,
    id: UUID = PARTNER_ID,
) = PrincipalKey(type, id)

/** 지정하려는 매니저. 기본값은 파트너(점주) 계정이다. */
fun newStoreManagerAssignment(
    storeId: StoreId = StoreId(101),
    principal: PrincipalKey = principalKey(),
) = NewStoreManagerAssignment(storeId, principal)

/** 저장된 매니저 지정. */
fun storeManagerAssignment(
    storeId: StoreId = StoreId(101),
    principal: PrincipalKey = principalKey(),
    assignedAt: Instant = Instant.parse("2026-10-01T00:00:00Z"),
) = StoreManagerAssignment.reconstitute(storeId, principal, assignedAt)
