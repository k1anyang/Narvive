package com.narvive.app.service.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 意图路由与书名匹配的回归测试。
 *
 * 这两处都是「静默失效」型缺陷：路由不命中时不会有任何报错，
 * 只是本地数据不再注入，答案悄悄变差——因此必须有断言守住。
 */
class IntentRoutingTest {

    // ── 大小写 ──

    @Test
    fun `英文建议卡自己的文案必须命中对应意图`() {
        // 这些就是 App 自己展示并发送的文案（res/values-en/strings_chat.xml）。
        // 旧实现的大小写敏感正则命中不了首字母大写的英文，快路径形同虚设。
        assertTrue(
            "推荐建议卡应命中推荐意图",
            AiText.INTENT_RECOMMEND.containsMatchIn("Recommend what I should read"),
        )
        assertTrue(
            "日报建议卡应命中统计意图",
            AiText.INTENT_STATS.containsMatchIn("Summarize my reading report"),
        )
        assertTrue(
            "进度提问应命中进度意图",
            AiText.INTENT_PROGRESS.containsMatchIn("Continue reading"),
        )
    }

    @Test
    fun `中文关键词仍然命中`() {
        assertTrue(AiText.INTENT_STATS.containsMatchIn("帮我总结阅读日报"))
        assertTrue(AiText.INTENT_RECOMMEND.containsMatchIn("推荐我读什么"))
        assertTrue(AiText.INTENT_PROGRESS.containsMatchIn("我上次读到哪了"))
    }

    @Test
    fun `中英并集让中文界面也能识别英文关键词`() {
        assertTrue(AiText.INTENT_STATS.containsMatchIn("show me my stats"))
        assertTrue(AiText.INTENT_RECOMMEND.containsMatchIn("what should I read next"))
    }

    @Test
    fun `覆盖型意图用于区分总结与细节提问`() {
        assertTrue(AiText.INTENT_COVERAGE.containsMatchIn("请总结当前章节"))
        assertTrue(AiText.INTENT_COVERAGE.containsMatchIn("这一章讲了什么"))
        assertTrue(AiText.INTENT_COVERAGE.containsMatchIn("Summarize this chapter"))
        assertFalse(
            "细节提问不应被当成覆盖型任务",
            AiText.INTENT_COVERAGE.containsMatchIn("慕容雪为什么要离开"),
        )
    }

    // ── 书名匹配 ──

    @Test
    fun `完整书名出现时精确命中`() {
        assertTrue(IntentTitles.hit("帮我看看《活着》这本书", "活着", null))
        assertTrue(IntentTitles.hit("I am reading Dune again", "Dune", null))
    }

    @Test
    fun `含 the 的普通句子不会误命中英文书名`() {
        // 旧实现用任意 2 字窗口匹配，「the」「he」会命中 The Hobbit 之类的书名。
        assertFalse(
            "普通句子不应命中书名",
            IntentTitles.fuzzyHit("What is the weather like today", "The Hobbit"),
        )
        assertFalse(
            "只命中一小段也不算",
            IntentTitles.fuzzyHit("I like the way it works", "The Way of Kings"),
        )
    }

    @Test
    fun `标题大半出现才算模糊命中`() {
        assertTrue(IntentTitles.fuzzyHit("想聊聊 红楼梦 里的判词", "红楼梦"))
        assertFalse("两字标题不做模糊匹配", IntentTitles.fuzzyHit("我今天活着挺好", "活着"))
    }

    @Test
    fun `书名号片段允许前缀匹配`() {
        assertTrue(IntentTitles.hit("《三体》好看吗", "三体全集", "三体"))
        assertFalse(IntentTitles.hit("随便聊聊", "三体全集", "流浪地球"))
    }
}
