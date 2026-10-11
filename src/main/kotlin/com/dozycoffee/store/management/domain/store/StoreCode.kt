package com.dozycoffee.store.management.domain.store

/**
 * 실물·사람·다른 회사 시스템을 기준으로 일하는 연동이 쓰는 매장 번호. 형식은 `ST-` + 8자리 일련번호(`ST-00000123`)다.
 * 지역·유형 같은 정보를 담지 않는다. 매장의 속성은 바뀔 수 있지만 이미 외부에 나간 코드는 바꿀 수 없기 때문이다(요구사항 1.1).
 *
 * 새로 발급할 때는 [create], DB에서 복원하거나 외부에서 받은 문자열은 생성자로 만든다. 어느 쪽이든 형식이 틀린 값은 만들어지지 않는다.
 */
@JvmInline
value class StoreCode(
    val value: String,
) {
    init {
        require(PATTERN.matches(value)) { "매장 코드 형식이 올바르지 않습니다: $value" }
    }

    override fun toString(): String = value

    companion object {
        private const val PREFIX = "ST-"
        private const val DIGITS = 8

        private const val MIN_SEQUENCE = 1L
        private val MAX_SEQUENCE = "9".repeat(DIGITS).toLong()

        private val PATTERN = Regex("${Regex.escape(PREFIX)}[0-9]{$DIGITS}")

        /** 전용 시퀀스(`store_code_seq`)에서 받은 일련번호로 새 코드를 만든다. */
        fun create(sequence: Long): StoreCode {
            require(sequence in MIN_SEQUENCE..MAX_SEQUENCE) {
                "매장 코드 일련번호는 $MIN_SEQUENCE~$MAX_SEQUENCE 범위여야 합니다: $sequence"
            }
            return StoreCode("$PREFIX${sequence.toString().padStart(DIGITS, '0')}")
        }
    }
}
