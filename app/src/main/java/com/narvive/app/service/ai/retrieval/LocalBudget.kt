package com.narvive.app.service.ai.retrieval

/**
 * 本地计算的软时间预算。
 *
 * 面向低配机：索引构建、逐句打分、词法扫描都是 O(章节字符数) 的本地工作，
 * 20 万字章节在中端机约几十毫秒，但在老设备上可能到几百毫秒。
 * 这里给出一个截止时间，超时后各环节转入**更省 CPU 但仍可用**的形态，
 * 而不是让用户对着转圈等，也不是直接放弃检索。
 *
 * 注意：这与协程取消不同——取消意味着用户离开了页面，结果会被丢弃；
 * 预算超时意味着仍要产出结果，只是精度降低。
 */
class LocalBudget private constructor(private val deadlineNanos: Long) {

    /**
     * 已过期判定。
     *
     * 用减法而不是 `nanoTime() > deadline`：nanoTime 的原点任意、理论上可为负，
     * 减法比较是标准的溢出安全写法。但**哨兵值必须取 0**——
     * 取 Long.MIN_VALUE 会让相减回绕成负数，反而被判成「未过期」。
     */
    fun expired(): Boolean = System.nanoTime() - deadlineNanos > 0

    val unlimited: Boolean get() = deadlineNanos == Long.MAX_VALUE

    companion object {
        /**
         * 默认预算。
         *
         * 400ms 的取舍：低于此值，用户几乎感觉不到；超过则已经比一次网络往返更值得优化。
         * 中端机通常用不到一半，低配机才会真正触发降级路径。
         */
        const val DEFAULT_MILLIS = 400L

        fun ofMillis(millis: Long = DEFAULT_MILLIS): LocalBudget =
            if (millis <= 0) LocalBudget(0L)
            else LocalBudget(System.nanoTime() + millis * 1_000_000L)

        /** 不限制（单测与短文本用） */
        val UNLIMITED: LocalBudget = LocalBudget(Long.MAX_VALUE)

        /** 立即过期（用于测试降级路径） */
        val EXPIRED: LocalBudget = LocalBudget(0L)
    }
}
