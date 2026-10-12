package com.dozycoffee.store.common.web

/**
 * 요청 DTO의 값을 검증하고 정리한다. 위반하면 [InvalidRequestException]을 던져 `400 INVALID_REQUEST`가 된다.
 * 문자열은 앞뒤 공백을 제거한 값으로 판단한다(요구사항 1.1). 길이는 `String.length`로 센다.
 */
object RequestValues {
    /** 값이 있어야 한다. */
    fun <T : Any> required(
        field: String,
        value: T?,
    ): T = value ?: throw InvalidRequestException(field)

    /** 앞뒤 공백을 제거했을 때 비어 있지 않고 [max]자 이하여야 한다. 제거한 값을 돌려준다. */
    fun text(
        field: String,
        value: String?,
        max: Int,
    ): String {
        val trimmed = required(field, value).trim()
        if (trimmed.isEmpty() || trimmed.length > max) throw InvalidRequestException(field)
        return trimmed
    }

    /** 없을 수 있다. 값이 있어도 앞뒤 공백을 제거한 뒤 비어 있으면 없는 것(`null`)으로 본다. 있다면 [max]자 이하여야 한다. */
    fun optionalText(
        field: String,
        value: String?,
        max: Int,
    ): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.length > max) throw InvalidRequestException(field)
        return trimmed
    }
}
