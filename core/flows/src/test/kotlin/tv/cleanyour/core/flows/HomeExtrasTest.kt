package tv.cleanyour.core.flows

import kotlinx.serialization.json.JsonPrimitive
import tv.cleanyour.core.spec.LauncherConfigDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class HomeExtrasTest {
    @Test fun noneLeavesTheLookAsItIs() {
        val doc = LauncherConfigDoc()
        assertSame(doc, HomeExtras.apply(doc, HomeExtras()))
    }

    @Test fun choicesGoInAndOldOnesGo() {
        val on = HomeExtras.apply(LauncherConfigDoc(), HomeExtras(signal = true, continueCard = true))
        assertEquals(JsonPrimitive(true), on.unknown[HomeExtras.SIGNAL])
        assertEquals(null, on.unknown[HomeExtras.SIDEBAR])
        assertEquals(JsonPrimitive(true), on.unknown[HomeExtras.CONTINUE])
        val off = HomeExtras.apply(on, HomeExtras(sidebar = true))
        assertEquals(setOf(HomeExtras.SIDEBAR), off.unknown.keys)
    }
}
