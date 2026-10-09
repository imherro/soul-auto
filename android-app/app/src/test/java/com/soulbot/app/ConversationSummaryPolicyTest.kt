package com.soulbot.app

import org.junit.Assert.*
import org.junit.Test

class ConversationSummaryPolicyTest {
    @Test fun `summary waits for twelve new records`() {
        assertNull(ConversationSummaryPolicy.refreshBatch(ConversationSummaryState(), messages(1, 11), 1000))
        assertEquals(12L, ConversationSummaryPolicy.refreshBatch(ConversationSummaryState(), messages(1, 12), 1000)!!.throughMessageId)
    }

    @Test fun `incremental summary never resubmits records before its cursor`() {
        val state = ConversationSummaryState("旧摘要", 20)
        assertEquals(messages(21, 32), ConversationSummaryPolicy.refreshBatch(state, messages(1, 32), 1000)!!.messages)
    }

    @Test fun `failed request cools down without advancing the cursor`() {
        val state = ConversationSummaryState("旧摘要", 20, updatedAt = 1000, lastAttemptAt = 2000)
        assertNull(ConversationSummaryPolicy.refreshBatch(state, messages(21, 40), 3000))
        assertNotNull(ConversationSummaryPolicy.refreshBatch(state, messages(21, 40), 2000 + ConversationSummaryPolicy.FAILURE_RETRY_MS))
    }

    @Test fun `successful summary can update once another twelve arrive`() {
        val state = ConversationSummaryState("摘要", 20, updatedAt = 2000, lastAttemptAt = 1500)
        assertNotNull(ConversationSummaryPolicy.refreshBatch(state, messages(21, 32), 2100))
    }

    @Test fun `large backlog is processed in bounded sequential batches`() {
        val batch = ConversationSummaryPolicy.refreshBatch(ConversationSummaryState(), messages(1, 150), 1000)!!
        assertEquals(120, batch.messages.size)
        val lengthy = messages(1, 30).map { it.copy(content = "长".repeat(2000)) }
        val bounded = ConversationSummaryPolicy.refreshBatch(ConversationSummaryState(), lengthy, 1000)!!
        assertEquals(7, bounded.messages.size)
        assertEquals(7L, bounded.throughMessageId)
    }

    @Test fun `summary input distinguishes speakers and includes observation dates`() {
        val batch = ConversationSummaryBatch(listOf(SummaryMessage(1, "in", "我养了一只猫", 1_760_000_000_000), SummaryMessage(2, "out", "明天再聊", 1_760_000_001_000)))
        val input = ConversationSummaryPolicy.input(ConversationSummaryState("旧资料"), batch)
        assertTrue(input.contains("旧资料"))
        assertTrue(input.contains("对方：我养了一只猫"))
        assertTrue(input.contains("我（可能为自动回复）：明天再聊"))
        assertTrue(input.contains("2025-"))
        assertTrue(ConversationSummaryPolicy.systemPrompt().contains("提议和双方确认"))
    }

    @Test fun `empty malformed and excessive model outputs do not replace valid memory`() {
        val valid = "对方资料：养猫\n近期话题：猫咪\n约定与待办：暂无"
        assertEquals(valid, ConversationSummaryPolicy.validatedSummary(valid))
        assertNull(ConversationSummaryPolicy.validatedSummary(null))
        assertNull(ConversationSummaryPolicy.validatedSummary("随便聊聊"))
        assertNull(ConversationSummaryPolicy.validatedSummary(valid + "长".repeat(1800)))
    }

    private fun messages(start: Int, end: Int) = (start..end).map { SummaryMessage(it.toLong(), "in", "消息$it", it * 1000L) }
}
