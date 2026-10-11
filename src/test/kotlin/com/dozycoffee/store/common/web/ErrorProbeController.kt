package com.dozycoffee.store.common.web

import com.dozycoffee.store.core.DomainException
import com.dozycoffee.store.core.ErrorCode
import com.dozycoffee.store.core.ErrorType
import com.dozycoffee.store.core.VersionConflictException
import org.springframework.context.annotation.Profile
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody

enum class ProbeErrorCode(
    override val type: ErrorType,
) : ErrorCode {
    PROBE_FAILED(ErrorType.INVALID_INPUT),
    PROBE_NOT_FOUND(ErrorType.NOT_FOUND),
    PROBE_CONFLICT(ErrorType.CONFLICT),
    PROBE_RULE_VIOLATED(ErrorType.BUSINESS_RULE_VIOLATION),
    ;

    override val code: String get() = name
}

class ProbeException(
    code: ProbeErrorCode,
) : DomainException(code, "프로브: ${code.name}")

data class ProbeBody(
    val name: String,
)

/**
 * 오류 응답 형식을 확인하는 테스트 전용 컨트롤러. `error-probe` 프로필을 켠 테스트에서만 등록되어 다른 테스트 컨텍스트에 섞이지 않는다.
 * 공개 경로(`/api/v1/public` 아래)라 토큰이 필요 없다.
 */
@Controller
@Profile(ErrorProbeController.PROFILE)
@RequestMapping("/api/v1/public/probe")
@ResponseBody
class ErrorProbeController {
    companion object {
        const val PROFILE = "error-probe"
    }

    @GetMapping("/domain/{code}")
    fun domain(
        @PathVariable code: ProbeErrorCode,
    ): String = throw ProbeException(code)

    @GetMapping("/version-conflict")
    fun versionConflict(): String = throw VersionConflictException(currentVersion = 7)

    @GetMapping("/unexpected")
    fun unexpected(): String = error("비밀 내부 정보: select * from stores")

    @GetMapping("/forbidden")
    fun forbidden(): String = throw AccessDeniedException("역할 없음")

    @PostMapping("/body")
    fun body(
        @RequestBody body: ProbeBody,
    ): String = body.name
}
