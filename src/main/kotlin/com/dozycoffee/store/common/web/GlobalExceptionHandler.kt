package com.dozycoffee.store.common.web

import com.dozycoffee.store.core.DomainException
import com.dozycoffee.store.core.VersionConflictException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException
import tools.jackson.core.JacksonException

private val log = KotlinLogging.logger {}

/**
 * 예외를 Problem Details로 바꾼다(docs/architecture/exception.md).
 * 도메인 예외는 `ErrorType`으로 상태를 정하고, `code`에는 `ErrorCode.code`를 그대로 담는다.
 */
@RestControllerAdvice
class GlobalExceptionHandler(
    private val problems: ProblemResponses,
) {
    @ExceptionHandler(DomainException::class)
    fun handleDomain(
        ex: DomainException,
        exchange: ServerWebExchange,
    ): ResponseEntity<ProblemDetail> = problems.respond(exchange, ex.errorCode.type.toHttpStatus(), ex.errorCode.code, ex.message)

    @ExceptionHandler(VersionConflictException::class)
    fun handleVersionConflict(
        ex: VersionConflictException,
        exchange: ServerWebExchange,
    ): ResponseEntity<ProblemDetail> =
        problems.respond(
            exchange,
            ex.errorCode.type.toHttpStatus(),
            ex.errorCode.code,
            ex.message,
            mapOf("currentVersion" to ex.currentVersion),
        )

    /** 값 검증에 실패한 요청. `detail`에 문제가 된 필드 이름을 담는다. */
    @ExceptionHandler(InvalidRequestException::class)
    fun handleInvalidRequest(
        ex: InvalidRequestException,
        exchange: ServerWebExchange,
    ): ResponseEntity<ProblemDetail> = problems.respond(exchange, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.message)

    /**
     * 본문을 읽을 수 없음, 필수 값 누락, 타입 불일치, 허용되지 않는 enum 값 등.
     * `detail`에는 문제가 된 파라미터나 JSON 필드의 이름만 담고, 입력한 값은 담지 않는다.
     */
    @ExceptionHandler(ServerWebInputException::class)
    fun handleInput(
        ex: ServerWebInputException,
        exchange: ServerWebExchange,
    ): ResponseEntity<ProblemDetail> {
        // 본문(@RequestBody) 오류는 컨트롤러 파라미터 이름이 아니라 JSON 필드 경로로 알린다. 쿼리 파라미터 같은 그 밖의 오류는 파라미터 이름을 쓴다.
        val field =
            jsonFieldPath(ex) ?: ex.methodParameter?.takeUnless { it.hasParameterAnnotation(RequestBody::class.java) }?.parameterName
        val detail = if (field != null) "요청 값이 올바르지 않습니다: $field" else "요청을 읽을 수 없습니다"
        return problems.respond(exchange, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", detail)
    }

    /** 없는 경로, 허용되지 않는 메서드 등 HTTP 수준의 오류. `code`는 HTTP 상태 이름이다. */
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(
        ex: ResponseStatusException,
        exchange: ServerWebExchange,
    ): ResponseEntity<ProblemDetail> {
        if (ex.statusCode.is5xxServerError) return internalError(ex, exchange)
        val code = HttpStatus.resolve(ex.statusCode.value())?.name ?: "HTTP_${ex.statusCode.value()}"
        return problems.respond(exchange, ex.statusCode, code).withHeaders(ex.headers)
    }

    /** 컨트롤러에서 던져진 인증·인가 예외. 스타터의 응답과 같은 코드를 쓴다. 포괄 핸들러가 500으로 삼키지 않게 따로 받는다. */
    @ExceptionHandler(AuthenticationException::class)
    fun handleUnauthenticated(exchange: ServerWebExchange): ResponseEntity<ProblemDetail> =
        problems
            .respond(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED")
            .withHeaders(HttpHeaders().apply { set(HttpHeaders.WWW_AUTHENTICATE, "Bearer") })

    @ExceptionHandler(AccessDeniedException::class)
    fun handleForbidden(exchange: ServerWebExchange): ResponseEntity<ProblemDetail> =
        problems.respond(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN")

    /**
     * 그 밖의 모든 예외. `require`/`check` 위반처럼 호출하는 코드가 잘못됐을 때도 여기로 온다.
     * 4xx로 보고하지 않고 서버 버그로 드러내며, 응답에는 내부 정보를 담지 않는다.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        ex: Exception,
        exchange: ServerWebExchange,
    ): ResponseEntity<ProblemDetail> = internalError(ex, exchange)

    /** JSON 본문 오류의 원인(Jackson)에서 문제가 된 필드 경로를 꺼낸다. 경로가 없으면(문법 오류 등) `null`이다. */
    private fun jsonFieldPath(ex: Throwable): String? {
        val path =
            generateSequence(ex.cause) { it.cause }
                .filterIsInstance<JacksonException>()
                .firstOrNull()
                ?.path
                .orEmpty()
        if (path.isEmpty()) return null
        return buildString {
            path.forEach { reference ->
                val name = reference.propertyName
                when {
                    name != null -> {
                        if (isNotEmpty()) append('.')
                        append(name)
                    }
                    reference.index >= 0 -> append('[').append(reference.index).append(']')
                }
            }
        }.takeIf { it.isNotEmpty() }
    }

    private fun internalError(
        ex: Exception,
        exchange: ServerWebExchange,
    ): ResponseEntity<ProblemDetail> {
        log.error(ex) { "처리하지 못한 오류: ${exchange.request.method} ${exchange.request.uri.rawPath}" }
        return problems.respond(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR")
    }

    private fun ResponseEntity<ProblemDetail>.withHeaders(extra: HttpHeaders): ResponseEntity<ProblemDetail> {
        if (extra.isEmpty()) return this
        return ResponseEntity
            .status(statusCode)
            .headers(headers)
            .headers(extra)
            .body(body)
    }
}
