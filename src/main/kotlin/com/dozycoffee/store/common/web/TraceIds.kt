package com.dozycoffee.store.common.web

import java.security.SecureRandom
import java.util.HexFormat

/**
 * 응답의 `traceId`(와 `X-Trace-Id` 헤더)를 정한다. 인증 스타터가 401·403에 쓰는 규칙과 같다.
 * 스타터의 구현은 `internal`이라 쓸 수 없어 같은 규칙으로 따로 둔다: 응답에 이미 있는 값, 요청 헤더의 값
 * (`[A-Za-z0-9-]{1,64}`만 믿는다), 없으면 새로 만든 값(16바이트, 소문자 hex 32자) 순이다.
 *
 * 스타터는 Micrometer Tracing이 있으면 현재 span의 trace id를 가장 먼저 쓴다. 이 프로젝트에 Micrometer Tracing을
 * 넣으면 여기에도 같은 우선순위를 더해야 두 응답의 `traceId`가 어긋나지 않는다.
 */
class TraceIds(
    private val random: SecureRandom = SecureRandom(),
) {
    fun resolve(
        existingResponseValue: String?,
        incomingRequestValue: String?,
    ): String =
        existingResponseValue?.takeIf { it.isNotBlank() }
            ?: incomingRequestValue?.takeIf { INCOMING_PATTERN.matches(it) }
            ?: generate()

    private fun generate(): String = HexFormat.of().formatHex(ByteArray(16).also(random::nextBytes))

    companion object {
        const val HEADER = "X-Trace-Id"
        private val INCOMING_PATTERN = Regex("[A-Za-z0-9-]{1,64}")
    }
}
