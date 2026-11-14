package tv.cleanyour.core.ops

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PackageState

// All apps: what the TV needs to run stays on, override or not; the rest of the denylist may go off
// after a confirmation, so the renderer writes its switch-off.
class CriticalTest {
    private fun state(persistent: Boolean = false) = PackageState(EnabledState.ENABLED, true, 1, null, persistent, false, null, false)

    @Test fun everyCriticalNameIsAlsoOnTheDenylist() {
        for (name in Critical.exact.keys) assertNotNull(Denylist.staticReason(name), name)
    }

    @Test fun theSystemAndTheWayBackStayOn() {
        for (name in listOf("android", "com.android.systemui", "com.android.tv.settings", "com.android.shell", "com.android.adbd",
            "com.google.android.tv.btservices", "com.android.networkstack.tethering", "com.android.wifi.resources")) {
            assertNotNull(Critical.staticReason(name), name)
            assertFailsWith<RenderRefused>(name) { ShellRenderer().render(Op.Disable(PackageName(name))) }
        }
        assertEquals(Critical.REASON_PERSISTENT, Critical.reason(PackageName("com.tcl.anything"), state(persistent = true)))
    }

    @Test fun protectedButNotCriticalCanBeSwitchedOff() {
        for (name in listOf("com.google.android.gms", "com.android.vending", "com.google.android.marvin.talkback", "com.tcl.channelplus")) {
            assertNotNull(Denylist.staticReason(name), name)
            assertNull(Critical.reason(PackageName(name), state()), name)
            val r = ShellRenderer().render(Op.Disable(PackageName(name)))
            assertTrue(r.lines.single().text.contains(name), r.lines.toString())
        }
    }

    @Test fun installsAndUninstallsStillRefuseTheWholeDenylist() {
        assertFailsWith<RenderRefused> { ShellRenderer().render(Op.Uninstall(PackageName("com.google.android.gms"))) }
        assertFailsWith<RenderRefused> { ShellRenderer().render(Op.DefaultState(PackageName("com.android.vending"))) }
    }
}
