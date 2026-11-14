package tv.cleanyour.core.probe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The remote in the app (RemoteInput): only key codes and plain text ever reach the shell.
class RemoteScriptsTest {
    @Test fun keysAreNumbersOnly() {
        assertEquals("input keyevent 23", ProbeScripts.keyEvent(23).text)
        assertEquals("input keyevent --longpress 3", ProbeScripts.keyEvent(3, long = true).text)
        assertFailsWith<IllegalArgumentException> { ProbeScripts.keyEvent(0) }
        assertFailsWith<IllegalArgumentException> { ProbeScripts.keyEvent(9999) }
    }

    @Test fun textIsPlainAndQuoted() {
        assertEquals("input text 'tom%sand%sjerry'", ProbeScripts.inputText("tom and jerry").text)
        for (bad in listOf("", "it's", "a;b", "\$(reboot)", "a`b`", "a\\b", "x|y", "a&b", "ح", "a\"b", "a>b")) {
            assertFalse(ProbeScripts.isSafeText(bad), bad)
        }
        assertTrue(ProbeScripts.isSafeText("Wi-Fi 5G: home@example.com"))
    }

    @Test fun speedUpWritesOnlyTheThreeScales() {
        assertTrue(ProbeScripts.animationWrite(0.5f).text.startsWith("settings put global window_animation_scale 0.5;"))
        assertFailsWith<IllegalArgumentException> { ProbeScripts.animationWrite(3f) }
        assertEquals("am kill-all", ProbeScripts.freeMemory().text)
    }
}
