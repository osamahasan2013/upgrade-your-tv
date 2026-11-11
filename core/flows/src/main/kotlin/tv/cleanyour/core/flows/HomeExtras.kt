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
data class HomeExtras(val signal: Boolean = false, val sidebar: Boolean = false, val continueCard: Boolean = false) {
    val any: Boolean get() = signal || sidebar || continueCard

    companion object {
        const val SIGNAL = "uytv_signal"
        const val SIDEBAR = "uytv_sidebar"
        const val CONTINUE = "uytv_continue"
        // The launcher that reads them (patches/launcher/0002-home-choices.patch).
        const val LAUNCHER_VERSION = 1002L

        // The doc with this TV's choices; with none, the keys are left out so nothing of an older choice stays.
        fun apply(doc: LauncherConfigDoc, extras: HomeExtras): LauncherConfigDoc {
            val unknown = LinkedHashMap(doc.unknown)
            unknown.remove(SIGNAL); unknown.remove(SIDEBAR); unknown.remove(CONTINUE)
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
        HomeExtras(signal = "signal" in on, sidebar = "sidebar" in on, continueCard = "continue" in on)
    } ?: HomeExtras()

    suspend fun save(id: TvId, e: HomeExtras) {
        if (!e.any) { store.delete(path(id)); return }
        val lines = listOfNotNull("signal".takeIf { e.signal }, "sidebar".takeIf { e.sidebar }, "continue".takeIf { e.continueCard })
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
            launcherOn = ours != null, launcherOld = ours != null && ours.versionCode < HomeExtras.LAUNCHER_VERSION)))
    }
}
