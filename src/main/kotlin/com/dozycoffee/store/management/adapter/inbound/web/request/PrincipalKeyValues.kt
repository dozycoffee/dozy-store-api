package com.dozycoffee.store.management.adapter.inbound.web.request

import com.dozycoffee.auth.core.PrincipalKey
import com.dozycoffee.auth.core.PrincipalType
import com.dozycoffee.store.common.web.InvalidRequestException
import com.dozycoffee.store.common.web.RequestValues
import com.dozycoffee.store.management.domain.manager.ASSIGNABLE_PRINCIPAL_TYPES

/**
 * 요청의 principal key를 읽는다. 형식은 Auth의 것을 그대로 쓴다(docs/api/README.md 요청·응답 형식): 계정 종류는 소문자
 * (`partner`, `employee`), 계정 ID는 소문자 UUID이고, 경로와 쿼리에서는 `partner:0199a3c4-…`(`sub`) 형식이다.
 * 토큰의 값과 그대로 비교해야 하므로 `auth-core`의 파싱을 쓰고 공백을 다듬거나 대소문자를 고쳐 주지 않는다.
 * 형식이 틀리면 [InvalidRequestException]으로 필드 이름과 함께 알린다.
 */
object PrincipalKeyValues {
    /** `partner:0199a3c4-…` 형식의 문자열. [field]는 오류에 담을 파라미터 이름이다. */
    fun fromSub(
        field: String,
        value: String?,
    ): PrincipalKey = PrincipalKey.fromSubOrNull(RequestValues.required(field, value)) ?: throw InvalidRequestException(field)

    /**
     * 매니저로 지정할 수 있는 계정(`partner`, `employee`)의 `partner:uuid` 형식. 계정 종류가 그 밖이면(`system`, `customer`) 요청 실수일 가능성이
     * 높아 형식 오류로 거부한다(docs/api/manager.md의 계정별 조회). 지정 가능한 종류는 도메인의 규칙을 그대로 쓴다.
     */
    fun assignableFromSub(
        field: String,
        value: String?,
    ): PrincipalKey = fromSub(field, value).also { if (it.type !in ASSIGNABLE_PRINCIPAL_TYPES) throw InvalidRequestException(field) }

    /**
     * 계정 종류와 계정 ID를 따로 받는 본문용. 모르는 종류(`admin`)와 UUID 형식 오류를 각 필드 이름으로 알린다.
     * `system`·`customer`는 올바른 종류라 여기서는 통과하고, 매니저로 지정할 수 없다는 판단은 도메인이 한다.
     */
    fun of(
        typeField: String,
        type: String?,
        idField: String,
        id: String?,
    ): PrincipalKey {
        val principalType =
            PrincipalType.fromClaimValueOrNull(RequestValues.required(typeField, type)) ?: throw InvalidRequestException(typeField)
        val principalId = PrincipalKey.parseIdOrNull(RequestValues.required(idField, id)) ?: throw InvalidRequestException(idField)
        return PrincipalKey(principalType, principalId)
    }
}
