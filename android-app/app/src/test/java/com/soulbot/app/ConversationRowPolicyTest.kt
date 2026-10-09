package com.soulbot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRowPolicyTest {
    private val window = UiRegion(0, 0, 720, 1280)
    private val root = ConversationUiNode(0, null, window)
    private val list = ConversationUiNode(1, 0, UiRegion(0, 100, 720, 1100), ConversationNodeRole.LIST)

    private fun row(id: Int, top: Int, name: String, unread: Boolean, legacy: Boolean = false): List<ConversationUiNode> =
        buildList {
            add(ConversationUiNode(id, 1, UiRegion(0, top, 720, top + 100),
                if (legacy) ConversationNodeRole.LEGACY_ROW else ConversationNodeRole.OTHER, clickable = true))
            add(ConversationUiNode(id + 1, id, UiRegion(110, top + 10, 650, top + 45)))
            add(ConversationUiNode(id + 2, id + 1, UiRegion(110, top + 10, 400, top + 40), ConversationNodeRole.NAME, name))
            add(ConversationUiNode(id + 3, id + 1, UiRegion(550, top + 10, 650, top + 40), ConversationNodeRole.TIME, "5分钟前"))
            // The unread badge is beside the avatar, not inside the name wrapper.
            if (unread) add(ConversationUiNode(id + 4, id, UiRegion(75, top + 5, 100, top + 30), ConversationNodeRole.UNREAD, "1"))
        }

    @Test fun missingLegacyRowIdStillFindsUnreadContactAndClickTarget() {
        val rows = ConversationRowPolicy.resolve(listOf(root, list) + row(10, 200, "Alice", true), window)
        assertEquals(1, rows.size)
        assertEquals("Alice", rows.single().name)
        assertEquals("5分钟前", rows.single().timeText)
        assertEquals(10, rows.single().clickNodeId)
        assertTrue(rows.single().unread)
        assertFalse(rows.single().usesLegacyId)
    }

    @Test fun legacyRowLayoutRemainsSupported() {
        val rows = ConversationRowPolicy.resolve(listOf(root, list) + row(10, 200, "Alice", true, true), window)
        assertTrue(rows.single().unread)
        assertTrue(rows.single().usesLegacyId)
    }

    @Test fun clickableNicknameDoesNotOverrideConversationRowClick() {
        val nodes = row(10, 200, "Alice", true).map {
            if (it.role == ConversationNodeRole.NAME) it.copy(clickable = true) else it
        }
        assertEquals(10, ConversationRowPolicy.resolve(listOf(root, list) + nodes, window).single().clickNodeId)
    }

    @Test fun offScreenBadgeIsIgnoredEvenIfItsVisibilityFlagIsStale() {
        val nodes = row(10, 200, "Alice", true).map {
            if (it.role == ConversationNodeRole.UNREAD) it.copy(bounds = UiRegion(75, 1300, 100, 1325)) else it
        }
        assertFalse(ConversationRowPolicy.resolve(listOf(root, list) + nodes, window).single().unread)
    }

    @Test fun adjacentContactsDoNotShareUnreadBadge() {
        val rows = ConversationRowPolicy.resolve(listOf(root, list) +
            row(10, 200, "Alice", false) + row(20, 300, "Bob", true), window)
        assertEquals(listOf("Alice", "Bob"), rows.map { it.name })
        assertEquals(listOf(false, true), rows.map { it.unread })
    }

    @Test fun hiddenRecycledBadgeIsIgnored() {
        val nodes = row(10, 200, "Alice", true).map {
            if (it.role == ConversationNodeRole.UNREAD) it.copy(visible = false) else it
        }
        assertFalse(ConversationRowPolicy.resolve(listOf(root, list) + nodes, window).single().unread)
    }

    @Test fun hiddenRecycledNameDoesNotMakeVisibleContactAmbiguous() {
        val hidden = row(20, 300, "Alice", true).map { it.copy(visible = false) }
        val rows = ConversationRowPolicy.resolve(listOf(root, list) + row(10, 200, "Alice", true) + hidden, window)
        assertEquals(1, rows.size)
        assertTrue(rows.single().unread)
    }

    @Test fun duplicateVisibleContactNamesAreNotRoutedToFirstMatch() {
        val nodes = listOf(root, list) + row(10, 200, "Alice", false) + row(20, 300, "Alice", true)
        assertTrue(ConversationRowPolicy.resolve(nodes, window).isEmpty())
    }

    @Test fun zeroCountBadgeIsNotUnread() {
        val nodes = row(10, 200, "Alice", true).map {
            if (it.role == ConversationNodeRole.UNREAD) it.copy(text = "0") else it
        }
        assertFalse(ConversationRowPolicy.resolve(listOf(root, list) + nodes, window).single().unread)
    }

    @Test fun badgeOutsideRowDoesNotTriggerReplyEvenWhenCloseToName() {
        val nodes = listOf(root, list) + row(10, 200, "Alice", false) +
            ConversationUiNode(90, 1, UiRegion(75, 205, 100, 230), ConversationNodeRole.UNREAD, "1")
        assertFalse(ConversationRowPolicy.resolve(nodes, window).single().unread)
    }

    @Test fun ambiguousParentWithMultipleContactsDoesNotAssignBadge() {
        val nodes = listOf(root, list,
            ConversationUiNode(10, 1, UiRegion(0, 200, 720, 400), clickable = true),
            ConversationUiNode(11, 10, UiRegion(110, 210, 400, 240), ConversationNodeRole.NAME, "Alice"),
            ConversationUiNode(12, 10, UiRegion(110, 310, 400, 340), ConversationNodeRole.NAME, "Bob"),
            ConversationUiNode(13, 10, UiRegion(75, 205, 100, 230), ConversationNodeRole.UNREAD, "1"))
        assertTrue(ConversationRowPolicy.resolve(nodes, window).none { it.unread })
    }

    @Test fun unknownListIdDoesNotRequireLegacyRowResource() {
        val unknownList = list.copy(role = ConversationNodeRole.OTHER)
        val rows = ConversationRowPolicy.resolve(listOf(root, unknownList) + row(10, 200, "Alice", true), window)
        assertTrue(rows.single().unread)
        assertEquals(10, rows.single().clickNodeId)
    }

    @Test fun namesOutsideKnownConversationListAreExcluded() {
        val nodes = listOf(root, list) + row(10, 200, "Alice", false) +
            ConversationUiNode(90, 0, UiRegion(100, 40, 400, 80), ConversationNodeRole.NAME, "Header")
        assertEquals(listOf("Alice"), ConversationRowPolicy.resolve(nodes, window).map { it.name })
    }

    @Test fun malformedParentCycleCannotLoopForever() {
        val nodes = listOf(
            ConversationUiNode(10, 11, UiRegion(0, 200, 720, 300), clickable = true),
            ConversationUiNode(11, 10, UiRegion(110, 210, 400, 240), ConversationNodeRole.NAME, "Alice"),
            ConversationUiNode(12, 10, UiRegion(75, 205, 100, 230), ConversationNodeRole.UNREAD, "1"))
        assertEquals(1, ConversationRowPolicy.resolve(nodes, window).size)
    }
}
