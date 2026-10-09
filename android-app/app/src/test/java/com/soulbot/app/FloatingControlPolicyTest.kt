package com.soulbot.app

import org.junit.Assert.*
import org.junit.Test

class FloatingControlPolicyTest {
    @Test fun duplicateDeliveryOfOneClickDoesNotStopTheTaskAgain() {
        val gate = FloatingClickGate()
        var running = false
        listOf(1000L, 1000L, 1001L).forEach { now ->
            if (gate.accept(now)) running = !running
        }
        assertTrue(running)
    }

    @Test fun repeatedRejectedClicksDoNotKeepTheButtonLocked() {
        val gate = FloatingClickGate()
        assertTrue(gate.accept(1000L))
        assertFalse(gate.accept(1200L))
        assertFalse(gate.accept(1499L))
        assertTrue(gate.accept(1500L))
    }

    @Test fun ASeparateStopClickIsAcceptedAfterTheShortGuardInterval() {
        val gate = FloatingClickGate()
        var running = false
        listOf(1000L, 1100L, 1700L).forEach { now ->
            if (gate.accept(now)) running = !running
        }
        assertFalse(running)
    }

    @Test fun firstClickIsAcceptedEvenAtZeroElapsedTime() {
        assertTrue(FloatingClickGate().accept(0L))
    }

    @Test fun recreatingTheFloatingServiceDoesNotInheritAnOldClickLock() {
        assertTrue(FloatingClickGate().accept(1000L))
        assertTrue(FloatingClickGate().accept(1001L))
    }

    @Test fun startDuringStopRestartsImmediatelyAfterCleanup() {
        assertEquals(0L, WorkerRestartPolicy.delayMs(true, true, true))
    }

    @Test fun cancellingQueuedStartNeverRestarts() {
        assertNull(WorkerRestartPolicy.delayMs(false, true, true))
    }

    @Test fun oldServiceCannotRestartOverTheNewService() {
        assertNull(WorkerRestartPolicy.delayMs(true, false, true))
    }

    @Test fun genuineUnexpectedExitRetainsRecoveryBackoff() {
        assertEquals(3000L, WorkerRestartPolicy.delayMs(true, true, false))
    }
}
