package tv.cleanyour.core.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.planner.SwitchOffRule
import tv.cleanyour.core.spec.LauncherConfigDoc
import tv.cleanyour.core.store.DocumentStore

// Upgrade Your TV 1.8.0: three home screen choices on top of the look (launcher 3.0-uytv2, UytvHome):
// dot-matrix type ("Signal"), the apps down a frosted sidebar instead of the row, and a "Continue in" card
// for the app Home opened last. Kept on the phone for each TV and written into every look sent to it
// (LookCarry, Setup) as carried keys the launcher's own Config does not read; saving sends the saved look
// again (the Home screen page, "again"), which asks for the screen lock.
enum class NameSize(val id: String) { LARGE("large"), SMALL("small"), HIDDEN("hidden") }

// 1.9.0 (launcher 3.0-uytv4, UytvTheme): the themes, by the id the launcher reads.
enum class HomeTheme(val id: String) {
    NONE("none"), GLASS("glass"), SIGNAL("signal"), SIGNAL_DARK("signal_dark"), GAMING("gaming"), ISLAMIC("islamic"),
    HALLOWEEN("halloween"), HORROR("horror"), ANIME("anime");
    companion object { fun of(id: String?): HomeTheme = entries.firstOrNull { it.id == id } ?: NONE }
}

data class HomeExtras(
    val signal: Boolean = false, val sidebar: Boolean = false, val continueCard: Boolean = false,
    // 1.8.3 (launcher 3.0-uytv3): the apps' names, the sidebar's side, and the dock alone with All apps.
    val names: NameSize = NameSize.LARGE, val sideRight: Boolean = false, val dockOnly: Boolean = false,
    // 1.9.0: the theme, whether its picture shows (over the look's own), and Home at every power-on (the helper's
    // power_on_home_always: with the Home button helper on, the new home screen comes up when the TV is switched on).
    val theme: HomeTheme = HomeTheme.NONE, val themePicture: Boolean = false, val openAtStart: Boolean = false,
    // 1.9.2 (launcher 3.0-uytv5): with a left sidebar, the clock moves to the right (false keeps it left).
    val clockOpposite: Boolean = true,
) {
    val any: Boolean get() = signal || sidebar || continueCard || names != NameSize.LARGE || sideRight || dockOnly ||
        theme != HomeTheme.NONE || openAtStart || !clockOpposite

    // A theme brings its own way of setting out Home; the person can change each part after.
    fun withTheme(t: HomeTheme): HomeExtras = when (t) {
        HomeTheme.SIGNAL -> copy(theme = t, themePicture = true, signal = true, sidebar = false)
        HomeTheme.SIGNAL_DARK -> copy(theme = t, themePicture = true, signal = true, sidebar = true, sideRight = true)
        HomeTheme.GLASS -> copy(theme = t, themePicture = true, signal = false, sidebar = true, sideRight = false, continueCard = true)
        HomeTheme.NONE -> copy(theme = t, themePicture = false)
        else -> copy(theme = t, themePicture = true, signal = false, sidebar = false, dockOnly = true)
    }

    companion object {
        const val SIGNAL = "uytv_signal"
        const val SIDEBAR = "uytv_sidebar"
        const val CONTINUE = "uytv_continue"
        const val NAMES = "uytv_names"
        const val SIDE = "uytv_side"
        const val DOCK_ONLY = "uytv_dock_only"
        const val THEME = "uytv_theme"
        const val THEME_PICTURE = "uytv_theme_bg"
        const val POWER_ON_HOME = "power_on_home_always"
        const val CLOCK_OPPOSITE = "uytv_clock_opposite"
        // The launcher that reads them (patches/launcher/0002-home-choices.patch).
        const val LAUNCHER_VERSION = 1002L
        // The launcher that reads names, side and dock only (patches/launcher/0003).
        const val LAUNCHER_VERSION_3 = 1003L
        // The launcher with the themes and the TV's own Style page (patches/launcher/0004).
        const val LAUNCHER_VERSION_4 = 1004L
        // The clock across from a left sidebar, the All apps icon (patches/launcher/0005).
        const val LAUNCHER_VERSION_5 = 1005L

        // The doc with this TV's choices; with none, the keys are left out so nothing of an older choice stays.
        fun apply(doc: LauncherConfigDoc, extras: HomeExtras): LauncherConfigDoc {
            val unknown = LinkedHashMap(doc.unknown)
            for (k in listOf(SIGNAL, SIDEBAR, CONTINUE, NAMES, SIDE, DOCK_ONLY, THEME, THEME_PICTURE, POWER_ON_HOME, CLOCK_OPPOSITE)) unknown.remove(k)
            if (!extras.clockOpposite) unknown[CLOCK_OPPOSITE] = JsonPrimitive(false)
            if (extras.openAtStart) unknown[POWER_ON_HOME] = JsonPrimitive(true)
            if (extras.theme != HomeTheme.NONE) unknown[THEME] = JsonPrimitive(extras.theme.id)
            if (extras.theme != HomeTheme.NONE && extras.themePicture) unknown[THEME_PICTURE] = JsonPrimitive(true)
            if (extras.names != NameSize.LARGE) unknown[NAMES] = JsonPrimitive(extras.names.id)
            if (extras.sideRight) unknown[SIDE] = JsonPrimitive("right")
            if (extras.dockOnly) unknown[DOCK_ONLY] = JsonPrimitive(true)
            if (extras.signal) unknown[SIGNAL] = JsonPrimitive(true)
            if (extras.sidebar) unknown[SIDEBAR] = JsonPrimitive(true)
            if (extras.continueCard) unknown[CONTINUE] = JsonPrimitive(true)
            return if (unknown == doc.unknown) doc else doc.copy(unknown = unknown)
        }
    }
}

class HomeExtrasStore(private val store: DocumentStore) {
    private fun path(id: TvId) = "tvs/${id.value}/home-extras.txt"

    suspend fun load(id: TvId): HomeExtras = store.read(path(id)) { bytes ->
        val on = bytes.decodeToString().lines().map { it.trim() }.toSet()
        HomeExtras(signal = "signal" in on, sidebar = "sidebar" in on, continueCard = "continue" in on,
            names = NameSize.entries.firstOrNull { "names=${it.id}" in on } ?: NameSize.LARGE,
            sideRight = "side=right" in on, dockOnly = "dockonly" in on,
            theme = HomeTheme.of(on.firstOrNull { it.startsWith("theme=") }?.removePrefix("theme=")),
            themePicture = "themepicture" in on, openAtStart = "atstart" in on, clockOpposite = "clocksame" !in on)
    } ?: HomeExtras()

    suspend fun save(id: TvId, e: HomeExtras) {
        if (!e.any) { store.delete(path(id)); return }
        val lines = listOfNotNull("signal".takeIf { e.signal }, "sidebar".takeIf { e.sidebar }, "continue".takeIf { e.continueCard },
            "names=${e.names.id}".takeIf { e.names != NameSize.LARGE }, "side=right".takeIf { e.sideRight }, "dockonly".takeIf { e.dockOnly },
            "theme=${e.theme.id}".takeIf { e.theme != HomeTheme.NONE }, "themepicture".takeIf { e.themePicture }, "atstart".takeIf { e.openAtStart }, "clocksame".takeIf { !e.clockOpposite })
        store.write(path(id), lines.joinToString("") { "$it\n" }.encodeToByteArray())
    }
}

data class HomeExtrasState(
    val tvName: String = "",
    val loading: Boolean = true,
    val extras: HomeExtras = HomeExtras(),
    val saved: HomeExtras = HomeExtras(),
    val launcherOn: Boolean = false,      // our launcher is on the TV (as Home or as an app)
    val launcherOld: Boolean = false,     // it is older than the one that reads these choices
) {
    val changed: Boolean get() = extras != saved
}

sealed interface HomeExtrasIntent {
    data class Signal(val on: Boolean) : HomeExtrasIntent
    data class Sidebar(val on: Boolean) : HomeExtrasIntent
    data class Continue(val on: Boolean) : HomeExtrasIntent
    data class Names(val size: NameSize) : HomeExtrasIntent
    data class SideRight(val on: Boolean) : HomeExtrasIntent
    data class DockOnly(val on: Boolean) : HomeExtrasIntent
    data class Theme(val theme: HomeTheme) : HomeExtrasIntent
    data class ThemePicture(val on: Boolean) : HomeExtrasIntent
    data class OpenAtStart(val on: Boolean) : HomeExtrasIntent
    data class ClockOpposite(val on: Boolean) : HomeExtrasIntent
    data object Save : HomeExtrasIntent
    data object OpenLook : HomeExtrasIntent        // the Home screen page: looks, pictures, the person's own photo
    data object SetUpAgain : HomeExtrasIntent
}

class HomeExtrasMachine(
    private val d: ManageDeps,
    scope: CoroutineScope,
    private val tvId: TvId,
    private val store: HomeExtrasStore = HomeExtrasStore(d.documents),
) : Machine<HomeExtrasState, HomeExtrasIntent, ManageEffect>(HomeExtrasState(), scope) {

    private data class Loaded(val s: HomeExtrasState)

    init { scope.launch { load() } }

    override fun reduce(state: HomeExtrasState, event: Any): HomeExtrasState = when (event) {
        is Loaded -> event.s
        is HomeExtrasIntent.Signal -> state.copy(extras = state.extras.copy(signal = event.on))
        is HomeExtrasIntent.Sidebar -> state.copy(extras = state.extras.copy(sidebar = event.on))
        is HomeExtrasIntent.Continue -> state.copy(extras = state.extras.copy(continueCard = event.on))
        is HomeExtrasIntent.Names -> state.copy(extras = state.extras.copy(names = event.size))
        is HomeExtrasIntent.SideRight -> state.copy(extras = state.extras.copy(sideRight = event.on))
        is HomeExtrasIntent.DockOnly -> state.copy(extras = state.extras.copy(dockOnly = event.on))
        is HomeExtrasIntent.Theme -> state.copy(extras = state.extras.withTheme(event.theme))
        is HomeExtrasIntent.ThemePicture -> state.copy(extras = state.extras.copy(themePicture = event.on))
        is HomeExtrasIntent.OpenAtStart -> state.copy(extras = state.extras.copy(openAtStart = event.on))
        is HomeExtrasIntent.ClockOpposite -> state.copy(extras = state.extras.copy(clockOpposite = event.on))
        else -> state
    }

    override fun handle(event: Any, before: HomeExtrasState, after: HomeExtrasState) {
        when (event) {
            HomeExtrasIntent.Save -> if (after.changed) scope.launch {
                attempt { store.save(tvId, after.extras) }
                send(Loaded(after.copy(saved = after.extras)))
                effect(ManageEffect.Navigate(if (after.launcherOld) "home/${tvId.value}" else "home/${tvId.value}/again"))
            }
            HomeExtrasIntent.OpenLook -> effect(ManageEffect.Navigate("home/${tvId.value}"))
            HomeExtrasIntent.SetUpAgain -> effect(ManageEffect.Navigate("setup/${tvId.value}"))
            else -> {}
        }
    }

    private suspend fun load() {
        val tv = d.tv(tvId)
        val saved = attempt { store.load(tvId) }.getOrNull() ?: HomeExtras()
        val snap = attempt { d.snapshots.latest(tvId) }.getOrNull()
        val ours = snap?.packages?.get(SwitchOffRule.OUR_LAUNCHER)
        send(Loaded(HomeExtrasState(tvName = tv?.nickname.orEmpty(), loading = false, extras = saved, saved = saved,
            launcherOn = ours != null, launcherOld = ours != null && ours.versionCode < HomeExtras.LAUNCHER_VERSION_5)))
    }
}
