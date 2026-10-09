package com.soulbot.app

/** One physical click may be delivered twice by an emulator's input bridge. */
internal class FloatingClickGate(private val intervalMs: Long = 500L) {
    private var lastAcceptedAt: Long? = null

    fun accept(now: Long): Boolean {
        val previous = lastAcceptedAt
        if (previous != null && now - previous < intervalMs) return false
        lastAcceptedAt = now
        return true
    }
}

internal object WorkerRestartPolicy {
    fun delayMs(shouldRun: Boolean, ownsState: Boolean, queuedStart: Boolean): Long? = when {
        !shouldRun || !ownsState -> null
        queuedStart -> 0L
        else -> 3000L
    }
}
