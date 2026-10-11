package com.dozycoffee.store.common.web

import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import java.net.URI

/**
 * RFC 9457 Problem Details 응답을 만든다(docs/api/README.md 오류 응답).
 * 인증 스타터가 401·403을 같은 형식으로 응답하므로, `traceId`도 스타터와 같은 규칙(`TraceIds`)으로 정해 한 API의 오류 형식을 하나로 유지한다.
 */
@Component
class ProblemResponses {
    private val traceIds = TraceIds()

    fun respond(
        exchange: ServerWebExchange,
        status: HttpStatusCode,
        code: String,
        detail: String? = null,
        extensions: Map<String, Any?> = emptyMap(),
    ): ResponseEntity<ProblemDetail> {
        val traceId =
            traceIds.resolve(
                exchange.response.headers.getFirst(TraceIds.HEADER),
                exchange.request.headers.getFirst(TraceIds.HEADER),
            )
        val problem =
            ProblemDetail.forStatus(status).apply {
                type = URI.create(TYPE_BASE + code.lowercase().replace('_', '-'))
                title = code.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
                this.detail = detail
                instance = URI.create(exchange.request.uri.rawPath)
                setProperty("code", code)
                setProperty("traceId", traceId)
                extensions.forEach { (name, value) -> setProperty(name, value) }
            }
        return ResponseEntity
            .status(status)
            .header(TraceIds.HEADER, traceId)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem)
    }

    private companion object {
        const val TYPE_BASE = "https://docs.dozycoffee.com/errors/"
    }
}
