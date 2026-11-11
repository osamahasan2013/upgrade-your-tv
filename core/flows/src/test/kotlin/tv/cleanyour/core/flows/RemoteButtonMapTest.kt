package tv.cleanyour.core.flows

import kotlinx.serialization.json.JsonPrimitive
import tv.cleanyour.core.spec.LauncherConfigCodec
import tv.cleanyour.core.spec.LauncherConfigDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RemoteButtonMapTest {
    @Test fun noButtonsLeavesTheLookAlone() {
        val doc = LauncherConfigDoc(unknown = mapOf("key_filter" to JsonPrimitive(false)))
        assertSame(doc, RemoteButtonMap.apply(doc, emptyList()))
    }

    @Test fun buttonsGoInAsTheLauncherReadsThem() {
        val doc = LauncherConfigDoc(unknown = mapOf("key_filter" to JsonPrimitive(false)))
        val out = RemoteButtonMap.apply(doc, listOf(
            ButtonMapping(RemoteButton.RED, false, "com.netflix.ninja"),
            ButtonMapping(RemoteButton.HOME_HOLD, true, "settings"),
        ))
        assertEquals(listOf("KEYCODE_PROG_RED:com.netflix.ninja", "hold:KEYCODE_HOME:settings"), out.appKeys)
        assertEquals(JsonPrimitive(true), out.unknown["key_filter"])
        // The codec takes it as it is (no throw) and keeps both keys.
        val bytes = LauncherConfigCodec.encode(out).decodeToString()
        kotlin.test.assertTrue("app_keys" in bytes && "key_filter" in bytes, bytes)
    }
}
