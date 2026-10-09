package com.soulbot.app

internal enum class ConversationNodeRole { OTHER, LIST, LEGACY_ROW, NAME, TIME, UNREAD }

/** Only list metadata is retained; message bodies are not needed for row routing. */
internal data class ConversationUiNode(
    val id: Int,
    val parentId: Int?,
    val bounds: UiRegion,
    val role: ConversationNodeRole = ConversationNodeRole.OTHER,
    val text: String = "",
    val visible: Boolean = true,
    val clickable: Boolean = false,
)

internal data class ResolvedConversationRow(
    val name: String,
    val timeText: String,
    val bounds: UiRegion,
    val clickNodeId: Int,
    val unread: Boolean,
    val usesLegacyId: Boolean,
)

/** Associates metadata by tree ancestry, never by proximity to another contact. */
internal object ConversationRowPolicy {
    fun resolve(nodes: List<ConversationUiNode>, window: UiRegion): List<ResolvedConversationRow> {
        val byId = nodes.associateBy(ConversationUiNode::id)
        fun ancestors(node: ConversationUiNode): List<ConversationUiNode> {
            val result = mutableListOf<ConversationUiNode>()
            val visited = mutableSetOf<Int>()
            var current: ConversationUiNode? = node
            while (current != null && visited.add(current.id)) {
                result += current
                current = current.parentId?.let(byId::get)
            }
            return result
        }
        fun visible(node: ConversationUiNode): Boolean =
            node.visible && node.bounds.isValid && node.bounds.intersect(window) != null

        val chains = nodes.associate { it.id to ancestors(it) }
        fun within(node: ConversationUiNode, container: ConversationUiNode): Boolean =
            chains[node.id].orEmpty().any { it.id == container.id }

        val lists = nodes.filter { it.role == ConversationNodeRole.LIST && visible(it) }
        val names = nodes.filter { node ->
            node.role == ConversationNodeRole.NAME && visible(node) && node.text.isNotBlank() &&
                (lists.isEmpty() || lists.any { within(node, it) })
        }
        val nameCounts = mutableMapOf<Int, Int>()
        for (name in names) {
            for (ancestor in chains[name.id].orEmpty()) {
                nameCounts[ancestor.id] = nameCounts.getOrDefault(ancestor.id, 0) + 1
            }
        }
        val duplicateNames = names.groupingBy { it.text.trim() }.eachCount()
        val badges = nodes.filter {
            it.role == ConversationNodeRole.UNREAD && visible(it) &&
                it.text.trim().toIntOrNull() != 0
        }
        val times = nodes.filter { it.role == ConversationNodeRole.TIME && visible(it) }

        return names.mapNotNull { name ->
            // Reply routing uses the displayed name. Two equal names cannot be
            // safely distinguished by the existing sender, so leave them alone.
            if (duplicateNames[name.text.trim()] != 1) return@mapNotNull null
            val rowAncestors = chains[name.id].orEmpty().takeWhile {
                it.parentId != null && it.role != ConversationNodeRole.LIST &&
                    nameCounts[it.id] == 1
            }.filter {
                visible(it) && it.bounds.height <= maxOf(window.height / 3, name.bounds.height * 4)
            }
            val row = rowAncestors.lastOrNull() ?: return@mapNotNull null
            // A clickable nickname/avatar can open a profile. Prefer the outer
            // single-contact row, which is the list's conversation click target.
            val clickNode = rowAncestors.lastOrNull { it.clickable } ?: name
            val time = times.filter { within(it, row) }.singleOrNull()?.text.orEmpty()
            ResolvedConversationRow(
                name = name.text.trim(),
                timeText = time,
                bounds = row.bounds,
                clickNodeId = clickNode.id,
                unread = badges.any { within(it, row) },
                usesLegacyId = rowAncestors.any { it.role == ConversationNodeRole.LEGACY_ROW },
            )
        }.sortedBy { it.bounds.top }
    }
}
