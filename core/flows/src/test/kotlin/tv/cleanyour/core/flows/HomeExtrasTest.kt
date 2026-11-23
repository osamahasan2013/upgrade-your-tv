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

    @Test fun namesSideAndDockOnlyGoInAsTheLauncherReadsThem() {
        val doc = HomeExtras.apply(LauncherConfigDoc(), HomeExtras(names = NameSize.HIDDEN, sideRight = true, dockOnly = true))
        assertEquals(JsonPrimitive("hidden"), doc.unknown[HomeExtras.NAMES])
        assertEquals(JsonPrimitive("right"), doc.unknown[HomeExtras.SIDE])
        assertEquals(JsonPrimitive(true), doc.unknown[HomeExtras.DOCK_ONLY])
    }

    @Test fun aThemeGoesInWithItsPictureAndPowerOnHome() {
        val e = HomeExtras().withTheme(HomeTheme.ISLAMIC).copy(openAtStart = true)
        val doc = HomeExtras.apply(LauncherConfigDoc(), e)
        assertEquals(JsonPrimitive("islamic"), doc.unknown[HomeExtras.THEME])
        assertEquals(JsonPrimitive(true), doc.unknown[HomeExtras.THEME_PICTURE])
        assertEquals(JsonPrimitive(true), doc.unknown[HomeExtras.POWER_ON_HOME])
        assertEquals(true, e.dockOnly)
    }

    // 1.9.3: names have one answer. Hidden here turns off the look's names under every app and the dock's title;
    // Large and Small leave the look's choice as it was.
    @Test fun hiddenNamesWinOverTheLooksNames() {
        val look = LauncherConfigDoc(dockTitle = true, unknown = mapOf(HomeExtras.ALWAYS_NAMES to JsonPrimitive(true)))
        val hidden = HomeExtras.apply(look, HomeExtras(names = NameSize.HIDDEN))
        assertEquals(JsonPrimitive(false), hidden.unknown[HomeExtras.ALWAYS_NAMES])
        assertEquals(false, hidden.dockTitle)
        val small = HomeExtras.apply(look, HomeExtras(names = NameSize.SMALL))
        assertEquals(JsonPrimitive(true), small.unknown[HomeExtras.ALWAYS_NAMES])
        assertEquals(true, small.dockTitle)
    }
}
