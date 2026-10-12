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

    /** 양의 정수(ID)여야 한다. 경로 변수와 쿼리 파라미터는 문자열로 받아 여기서 검증한다. */
    fun positiveLong(
        field: String,
        value: String?,
    ): Long {
        val number = required(field, value).trim().toLongOrNull()
        if (number == null || number <= 0) throw InvalidRequestException(field)
        return number
    }

    /**
     * 쉼표로 구분한 양의 정수 목록. 값이 없거나 비어 있으면 빈 목록이다.
     * 항목이 비었거나 숫자가 아니거나 [max]개를 넘으면 거부한다. 같은 값이 여러 번 와도 한 번만 담는다.
     */
    fun positiveLongs(
        field: String,
        value: String?,
        max: Int,
    ): Set<Long> {
        if (value.isNullOrBlank()) return emptySet()
        val items = value.split(',')
        if (items.size > max) throw InvalidRequestException(field)
        return items.map { positiveLong(field, it) }.toSet()
    }

    /**
     * 쉼표로 구분한 enum 이름 목록(대문자 이름 그대로). 값이 없거나 비어 있으면 빈 집합이다.
     * 항목이 비었거나 허용되지 않는 값이면 거부한다.
     */
    fun <E : Enum<E>> enums(
        field: String,
        value: String?,
        type: Class<E>,
    ): Set<E> {
        if (value.isNullOrBlank()) return emptySet()
        return value
            .split(',')
            .map { item -> type.enumConstants.firstOrNull { it.name == item.trim() } ?: throw InvalidRequestException(field) }
            .toSet()
    }

    /** 정수 쿼리 파라미터. 없으면 [default]이고, 있으면 정수이면서 [range] 안이어야 한다. */
    fun int(
        field: String,
        value: String?,
        default: Int,
        range: IntRange,
    ): Int {
        if (value == null) return default
        val number = value.trim().toIntOrNull()
        if (number == null || number !in range) throw InvalidRequestException(field)
        return number
    }
}
