package com.soulbot.app

import org.junit.Assert.*
import org.junit.Test

class ConversationContextPolicyTest {
    @Test fun `short visible viewport gains older stored context without duplicates`() {
        val stored = thread(1, 45)
        val visible = thread(40, 48)
        assertEquals(thread(1, 48), ConversationContextPolicy.recentContext(stored, visible))
    }

    @Test fun `recent context has sixty messages instead of twenty`() {
        assertEquals(thread(41, 100), ConversationContextPolicy.recentContext(thread(1, 95), thread(90, 100)))
    }

    @Test fun `contained viewport is not appended again`() {
        assertEquals(thread(1, 30), ConversationContextPolicy.recentContext(thread(1, 30), thread(5, 15)))
        assertTrue(ConversationMemoryLogic.messagesAfterOverlap(thread(1, 30), thread(5, 15)).isEmpty())
    }

    @Test fun `legitimate repeated messages and both speakers are preserved`() {
        val stored = listOf("in" to "嗯", "out" to "行", "in" to "嗯", "out" to "继续")
        val visible = stored.takeLast(2) + ("in" to "嗯")
        assertEquals(stored + ("in" to "嗯"), ConversationContextPolicy.recentContext(stored, visible))
    }

    @Test fun `long context retains a contiguous suffix and the latest message`() {
        assertEquals(thread(8, 10), ConversationContextPolicy.recentContext(thread(1, 10), emptyList(), characterBudget = 12))
        assertEquals(listOf("in" to "56789"), ConversationContextPolicy.recentContext(emptyList(), listOf("in" to "0123456789"), characterBudget = 5))
    }

    @Test fun `empty and invalid budgets return no context`() {
        assertTrue(ConversationContextPolicy.recentContext(emptyList(), emptyList()).isEmpty())
        assertTrue(ConversationContextPolicy.recentContext(thread(1, 10), emptyList(), limit = 0).isEmpty())
        assertTrue(ConversationContextPolicy.recentContext(thread(1, 10), emptyList(), characterBudget = 0).isEmpty())
    }

    private fun thread(start: Int, end: Int) = (start..end).map { "in" to "消息$it" }
}
