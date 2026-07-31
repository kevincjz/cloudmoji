package app.cloudmoji.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from `ios/Cloudmoji/CloudmojiTests/WordsViewTests.swift`'s
 * `beamingIsNotInterrupted` / `everyOtherMoodGivesWay` (the `arbitrate`
 * rule) and from the timing rules `WordsView.swift` encodes per-screen —
 * generalized here into the one shared machine `CLAUDE.md` and the Task 5
 * brief ask for.
 *
 * [FakeMascotScheduler] deliberately does **not** suppress a cancelled
 * entry's action, mirroring `SpeechControllerTest`'s
 * `FakeSpeechWatchdogScheduler`: the guard against a stale, superseded timer
 * resuming has to be `MascotMoodMachine`'s own generation counters, not the
 * scheduler's cooperation, so several tests below fire an old handle on
 * purpose to prove that guard actually holds.
 */
class MascotMoodMachineTest {

    /** Captures every scheduled callback rather than actually waiting, so a
     * test can fire — or deliberately re-fire a stale, already-superseded —
     * one on demand. */
    private class FakeMascotScheduler : MascotScheduler {
        private class Entry(val delayMillis: Long, val action: () -> Unit) : MascotScheduleHandle {
            override fun cancel() = Unit // see the class doc: cancellation correctness
            // is the machine's own generation guard, not the scheduler's.
        }

        private val scheduled = mutableListOf<Entry>()

        override fun schedule(delayMillis: Long, action: () -> Unit): MascotScheduleHandle {
            val entry = Entry(delayMillis, action)
            scheduled += entry
            return entry
        }

        val scheduledCount: Int get() = scheduled.size

        /** Fires the most recently scheduled callback. */
        fun fireLatest() = scheduled.last().action()

        /** Fires the callback scheduled at [index], in scheduling order —
         * for firing an old, already-superseded one on purpose. */
        fun fire(index: Int) = scheduled[index].action()

        /** The delay the most recently scheduled callback was armed with —
         * lets a test confirm *which* timer just got armed without needing
         * to fire it. */
        fun latestDelayMillis(): Long = scheduled.last().delayMillis
    }

    private data class Fixture(val machine: MascotMoodMachine, val scheduler: FakeMascotScheduler)

    private fun makeMachine(milestones: Set<Int> = MascotMoodMachine.DEFAULT_MILESTONES): Fixture {
        val scheduler = FakeMascotScheduler()
        return Fixture(MascotMoodMachine(scheduler, milestones), scheduler)
    }

    // MARK: - Initial state

    @Test
    fun `starts happy`() {
        val (machine, _) = makeMachine()
        assertEquals(MascotMood.Happy, machine.mood.value)
        assertEquals(0, machine.tapCount)
    }

    // MARK: - Tap -> excited -> (speaking | happy)

    @Test
    fun `a tap shows the excited face immediately`() {
        val (machine, _) = makeMachine()
        machine.onTap()
        assertEquals(MascotMood.Excited, machine.mood.value)
        assertEquals(1, machine.tapCount)
    }

    @Test
    fun `excited holds for 600ms then settles on happy if nothing is speaking`() {
        val (machine, scheduler) = makeMachine()
        machine.onTap()
        assertEquals(600L, scheduler.latestDelayMillis())
        scheduler.fireLatest()
        assertEquals(MascotMood.Happy, machine.mood.value)
    }

    @Test
    fun `excited hands off to speaking once the hold elapses if speech is still going`() {
        val (machine, scheduler) = makeMachine()
        machine.onTap()
        machine.onSpeechStarted()
        // Still excited — the 600ms floor holds even though speech is already going.
        assertEquals(MascotMood.Excited, machine.mood.value)
        scheduler.fireLatest()
        assertEquals(MascotMood.Speaking, machine.mood.value)
    }

    @Test
    fun `a word that finishes inside the excited hold does not cut the excited face short`() {
        val (machine, scheduler) = makeMachine()
        machine.onTap()
        machine.onSpeechStarted()
        machine.onSpeechFinished() // a very short word, done well inside 600ms
        assertEquals("the ~600ms excited floor must survive an early finish", MascotMood.Excited, machine.mood.value)
        scheduler.fireLatest()
        assertEquals(MascotMood.Happy, machine.mood.value)
    }

    @Test
    fun `rapid taps keep the excited face by re-arming the hold on every tap`() {
        val (machine, scheduler) = makeMachine()
        machine.onTap()
        machine.onTap() // before the first hold ever fires
        assertEquals(2, scheduler.scheduledCount)
        assertEquals(MascotMood.Excited, machine.mood.value)

        // The stale first hold (superseded by the second tap) must be a
        // no-op even though the fake scheduler does not suppress it.
        scheduler.fire(0)
        assertEquals(
            "a superseded excited-hold must not resolve the mood early",
            MascotMood.Excited,
            machine.mood.value,
        )

        scheduler.fireLatest() // the second tap's own hold
        assertEquals(MascotMood.Happy, machine.mood.value)
    }

    // MARK: - Speech without a tap (e.g. replay, category speech)

    @Test
    fun `speech starting with no recent tap goes straight to speaking`() {
        val (machine, _) = makeMachine()
        machine.onSpeechStarted()
        assertEquals(MascotMood.Speaking, machine.mood.value)
    }

    @Test
    fun `speech finishing with no recent tap returns to happy`() {
        val (machine, _) = makeMachine()
        machine.onSpeechStarted()
        machine.onSpeechFinished()
        assertEquals(MascotMood.Happy, machine.mood.value)
    }

    // MARK: - Beaming priority (CLAUDE.md rule 11)

    @Test
    fun `beaming outranks every other requested mood`() {
        for (requested in MascotMood.entries.filter { it != MascotMood.Beaming }) {
            assertEquals(
                "$requested was allowed to interrupt the celebration",
                MascotMood.Beaming,
                MascotMood.arbitrate(current = MascotMood.Beaming, requested = requested),
            )
        }
    }

    @Test
    fun `every other mood gives way, and beaming can always be entered`() {
        for (current in MascotMood.entries) {
            for (requested in MascotMood.entries) {
                if (current == MascotMood.Beaming && requested != MascotMood.Beaming) continue
                assertEquals(requested, MascotMood.arbitrate(current = current, requested = requested))
            }
        }
    }

    @Test
    fun `a tap during beaming does not change the mood`() {
        val (machine, scheduler) = makeMachine(milestones = setOf(1))
        machine.onTap() // the milestone tap itself
        scheduler.fireLatest() // celebrationDelay elapses -> beaming
        assertEquals(MascotMood.Beaming, machine.mood.value)

        machine.onTap() // an ordinary tap while beaming
        assertEquals("a tap must not interrupt the celebration", MascotMood.Beaming, machine.mood.value)
    }

    @Test
    fun `speech starting during beaming does not change the mood`() {
        val (machine, scheduler) = makeMachine(milestones = setOf(1))
        machine.onTap()
        // Resolve the excited-hold first so `excitedHoldActive` is false and
        // `onSpeechStarted` genuinely attempts (and must be blocked by
        // `arbitrate`, not merely skipped by the hold-window guard).
        scheduler.fire(0)
        scheduler.fireLatest() // celebrationDelay -> beaming
        assertEquals(MascotMood.Beaming, machine.mood.value)

        machine.onSpeechStarted()
        assertEquals("speech starting must not interrupt the celebration", MascotMood.Beaming, machine.mood.value)
    }

    @Test
    fun `speech finishing during beaming does not change the mood`() {
        val (machine, scheduler) = makeMachine(milestones = setOf(1))
        machine.onTap()
        machine.onSpeechStarted()
        scheduler.fire(0) // the excited hold from the milestone tap: -> speaking
        assertEquals(MascotMood.Speaking, machine.mood.value)
        scheduler.fireLatest() // celebrationDelay: -> beaming, overriding speaking
        assertEquals(MascotMood.Beaming, machine.mood.value)

        machine.onSpeechFinished()
        assertEquals("speech finishing must not interrupt the celebration", MascotMood.Beaming, machine.mood.value)
    }

    @Test
    fun `a request that arrives mid-beaming stays blocked even after beaming's own hold ends it`() {
        val (machine, scheduler) = makeMachine(milestones = setOf(1))
        machine.onTap() // milestone tap: excited, celebration armed
        val excitedHoldIndex = 0
        scheduler.fireLatest() // celebrationDelay -> beaming
        assertEquals(MascotMood.Beaming, machine.mood.value)

        // The excited-hold timer from the very tap that earned the
        // celebration is still outstanding and fires mid-beaming; the
        // request it makes must be rejected, not queued.
        scheduler.fire(excitedHoldIndex)
        assertEquals(
            "a request made while beaming must be blocked, not merely delayed",
            MascotMood.Beaming,
            machine.mood.value,
        )

        // Beaming's own hold ends the celebration (bypassing arbitrate) —
        // the blocked request from above has no further effect; it was
        // dropped, not queued.
        scheduler.fireLatest()
        assertEquals(MascotMood.Happy, machine.mood.value)
    }

    @Test
    fun `mood responds normally to a fresh tap once the celebration ends`() {
        val (machine, scheduler) = makeMachine(milestones = setOf(1))
        machine.onTap()
        scheduler.fireLatest() // celebrationDelay -> beaming
        scheduler.fireLatest() // celebrationHold -> happy
        assertEquals(MascotMood.Happy, machine.mood.value)

        machine.onTap()
        assertEquals(
            "a tap after the celebration ends must reach the mood like any other tap",
            MascotMood.Excited,
            machine.mood.value,
        )
    }

    // MARK: - Milestones

    @Test
    fun `milestones fire at 10, 25, 50 and 100 taps and nowhere else`() {
        val (machine, scheduler) = makeMachine()
        repeat(9) { machine.onTap() }
        // One scheduled entry per tap so far: just its excited-hold, no
        // celebration.
        assertEquals("no celebration should be armed before the 10th tap", 9, scheduler.scheduledCount)

        machine.onTap() // 10th tap: its own excited-hold, plus a celebration delay
        assertEquals(
            "the 10th tap must arm both its excited-hold and a celebration",
            11,
            scheduler.scheduledCount,
        )
    }

    @Test
    fun `a milestone tap arms a celebration after the 10th, 25th, 50th and 100th tap`() {
        val (machine, scheduler) = makeMachine()
        var armedCelebrations = 0
        var previousCount = 0
        for (n in 1..100) {
            machine.onTap()
            val armedThisTap = scheduler.scheduledCount - previousCount == 2 // hold + celebration delay
            if (armedThisTap) armedCelebrations += 1
            previousCount = scheduler.scheduledCount
            if (n in setOf(10, 25, 50, 100)) {
                assertTrue("tap $n should arm a celebration", armedThisTap)
            } else {
                assertTrue("tap $n should not arm a celebration", !armedThisTap)
            }
        }
        assertEquals(4, armedCelebrations)
    }

    @Test
    fun `milestone tap celebrates after a 500ms delay, holds 3000ms, then returns to happy`() {
        val (machine, scheduler) = makeMachine(milestones = setOf(1))
        machine.onTap()
        // Two timers armed by the milestone tap: the 600ms excited-hold and
        // the 500ms celebration delay.
        assertEquals(MascotMood.Excited, machine.mood.value)

        val delayEntryIndex = scheduler.scheduledCount - 1
        assertEquals(500L, scheduler.latestDelayMillis())
        scheduler.fire(delayEntryIndex)
        assertEquals(MascotMood.Beaming, machine.mood.value)

        assertEquals(3000L, scheduler.latestDelayMillis())
        scheduler.fireLatest()
        assertEquals(MascotMood.Happy, machine.mood.value)
    }

    @Test
    fun `milestone reached while speaking overrides the speaking face`() {
        val (machine, scheduler) = makeMachine(milestones = setOf(1))
        machine.onTap()
        machine.onSpeechStarted()
        scheduler.fire(0) // excited-hold elapses -> speaking (still speaking)
        assertEquals(MascotMood.Speaking, machine.mood.value)

        scheduler.fireLatest() // celebrationDelay -> beaming
        assertEquals(
            "a milestone must override the speaking face",
            MascotMood.Beaming,
            machine.mood.value,
        )
    }

    @Test
    fun `two milestones close together extend the celebration rather than layering it`() {
        val (machine, scheduler) = makeMachine(milestones = setOf(1, 2))
        machine.onTap() // 1st milestone
        val firstDelayIndex = scheduler.scheduledCount - 1
        scheduler.fire(firstDelayIndex) // -> beaming
        assertEquals(MascotMood.Beaming, machine.mood.value)

        machine.onTap() // 2nd milestone, while still beaming from the 1st
        val secondDelayIndex = scheduler.scheduledCount - 1
        scheduler.fire(secondDelayIndex) // re-affirms beaming
        assertEquals(MascotMood.Beaming, machine.mood.value)

        // The FIRST celebration's hold timer is stale — the second call to
        // celebrate() cancelled it. It must not end the celebration early.
        val firstHoldIndex = firstDelayIndex + 1
        scheduler.fire(firstHoldIndex)
        assertEquals(
            "a superseded celebration hold must not end an extended celebration early",
            MascotMood.Beaming,
            machine.mood.value,
        )

        // Only the second (current) hold actually ends it.
        scheduler.fireLatest()
        assertEquals(MascotMood.Happy, machine.mood.value)
    }

    @Test
    fun `default milestones match the product spec`() {
        assertEquals(setOf(10, 25, 50, 100), MascotMoodMachine.DEFAULT_MILESTONES)
    }

    @Test
    fun `timings match the product spec`() {
        assertEquals(600L, MascotMoodMachine.EXCITED_HOLD_MS)
        assertEquals(500L, MascotMoodMachine.CELEBRATION_DELAY_MS)
        assertEquals(3000L, MascotMoodMachine.CELEBRATION_HOLD_MS)
    }
}
