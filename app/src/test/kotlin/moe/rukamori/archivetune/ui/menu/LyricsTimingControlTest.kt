package moe.rukamori.archivetune.ui.menu

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsTimingControlTest {
    @Test fun smallStepsAccumulateAcrossZero() {
        val offset = (1..3).fold(-100) { value, _ -> adjustedLyricsOffset(value, 100) }
        assertEquals(200, offset)
    }
    @Test fun adjustmentStopsAtExistingLimits() {
        assertEquals(5000, adjustedLyricsOffset(4950, 100))
        assertEquals(-5000, adjustedLyricsOffset(-4950, -100))
    }
    @Test fun extremeInputCannotOverflow() {
        assertEquals(5000, adjustedLyricsOffset(Int.MAX_VALUE, 100))
        assertEquals(-5000, adjustedLyricsOffset(Int.MIN_VALUE, -100))
    }
}
