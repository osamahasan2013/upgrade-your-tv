package tv.cleanyour.core.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.planner.HomeHelper
import tv.cleanyour.core.probe.LaunchableApps
import tv.cleanyour.core.spec.HomeHelperKeys
import tv.cleanyour.core.spec.LauncherConfigDoc
import tv.cleanyour.core.store.DocumentStore

// Upgrade Your TV 1.7.0: what the remote's buttons do. The new home screen's HomeKeyService (the Home button
// helper, which must be on) reads app_keys from its config.json: a button, a tap or a hold, and the app or
// action it opens. The person's choice is kept on the phone for each TV (RemoteButtonMap) and goes into every
// look the app sends to that TV (LookCarry), with key_filter true, which the helper needs to see the buttons.
// Saving sends the TV's saved look again (the Home screen page, "again"), so the screen lock is asked there.
//
// Only buttons no app needs to move or choose are offered (never Back, the arrows, OK, Enter or Menu:
// ConfigCodecImpl.NAVIGATION_KEYCODES), and not Settings, which the helper always opens itself.
enum class RemoteButton(val keyName: String) {
    RED("KEYCODE_PROG_RED"), GREEN("KEYCODE_PROG_GREEN"), YELLOW("KEYCODE_PROG_YELLOW"), BLUE("KEYCODE_PROG_BLUE"),
    INPUT("KEYCODE_TV_INPUT"), LIVE_TV("KEYCODE_TV"), GUIDE("KEYCODE_GUIDE"), INFO("KEYCODE_INFO"), APPS("KEYCODE_ALL_APPS"),
    BOOKMARK("KEYCODE_BOOKMARK"), CAPTIONS("KEYCODE_CAPTIONS"), PLAY_PAUSE("KEYCODE_MEDIA_PLAY_PAUSE"), HOME_HOLD("KEYCODE_HOME");
    // Home is only offered held: a short press already opens the new home screen with the helper on.
    val holdOnly: Boolean get() = this == HOME_HOLD
}

// One mapping: the button, held or tapped, and its target as the launcher reads it (a package, "inputs",
// or one of the action words).
data class ButtonMapping(val button: RemoteButton, val hold: Boolean, val target: String) {
    val entry: String get() = (if (hold) "hold:" else "") + button.keyName + ":" + target
}

object ButtonTargets {
    const val NEW_HOME = "com.gathin.tvhome"
    val ACTIONS: List<String> = listOf("inputs", "settings", "wifi", "mute", "sleep", "screensaver", "recents")
    fun valid(target: String): Boolean = target in ACTIONS || runCatching { PackageName(target) }.isSuccess
}

class RemoteButtonMap(private val store: DocumentStore) {
    private fun path(id: TvId) = "tvs/${id.value}/buttons.txt"

    suspend fun load(id: TvId): List<ButtonMapping> = store.read(path(id)) { bytes ->
        bytes.decodeToString().lines().mapNotNull { line ->
            val hold = line.startsWith("hold:")
            val rest = line.removePrefix("hold:")
            val name = rest.substringBefore('=', "").trim()
            val target = rest.substringAfter('=', "").trim()
            val button = RemoteButton.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            if (!ButtonTargets.valid(target) || (button.holdOnly && !hold)) null else ButtonMapping(button, hold, target)
        }
    }.orEmpty()

    suspend fun save(id: TvId, map: List<ButtonMapping>) {
        if (map.isEmpty()) { store.delete(path(id)); return }
        store.write(path(id), map.joinToString("") { (if (it.hold) "hold:" else "") + it.button.name + "=" + it.target + "\n" }.encodeToByteArray())
    }

    companion object {
        // The doc with this TV's buttons: app_keys in the launcher's form and key_filter true. With none chosen
        // the doc is left as the look made it (its own app_keys, if any; the helper's key_filter false).
        fun apply(doc: LauncherConfigDoc, map: List<ButtonMapping>): LauncherConfigDoc {
            if (map.isEmpty()) return doc
            val keys = (doc.appKeys.orEmpty() + map.map { it.entry }).distinct()
            return doc.copy(appKeys = keys, unknown = LinkedHashMap(doc.unknown).apply { put(HomeHelperKeys.KEY_FILTER, JsonPrimitive(true)) })
        }
    }
}

data class TargetChoice(val target: String, val name: String)

data class RemoteButtonsState(
    val tvName: String = "",
    val loading: Boolean = true,
    val helperOn: Boolean = false,
    val map: List<ButtonMapping> = emptyList(),
    val saved: List<ButtonMapping> = emptyList(),
    val apps: List<TargetChoice> = emptyList(),
    val editing: Pair<RemoteButton, Boolean>? = null,   // the button and whether its hold is being chosen
) {
    val changed: Boolean get() = map.toSet() != saved.toSet()
    fun targetOf(b: RemoteButton, hold: Boolean): String? = map.firstOrNull { it.button == b && it.hold == hold }?.target
}

sealed interface RemoteButtonsIntent {
    data class Edit(val button: RemoteButton, val hold: Boolean) : RemoteButtonsIntent
    data class Choose(val target: String?) : RemoteButtonsIntent   // null: the button does what the TV makes it do
    data object CloseEdit : RemoteButtonsIntent
    data object Save : RemoteButtonsIntent
    data object SetUpHelper : RemoteButtonsIntent
}

class RemoteButtonsMachine(
    private val d: ManageDeps,
    scope: CoroutineScope,
    private val tvId: TvId,
    private val buttons: RemoteButtonMap = RemoteButtonMap(d.documents),
) : Machine<RemoteButtonsState, RemoteButtonsIntent, ManageEffect>(RemoteButtonsState(), scope) {

    private data class Loaded(val s: RemoteButtonsState)

    init { scope.launch { load() } }

    override fun reduce(state: RemoteButtonsState, event: Any): RemoteButtonsState = when (event) {
        is Loaded -> event.s
        is RemoteButtonsIntent.Edit -> state.copy(editing = event.button to (event.hold || event.button.holdOnly))
        RemoteButtonsIntent.CloseEdit -> state.copy(editing = null)
        is RemoteButtonsIntent.Choose -> state.editing?.let { (b, hold) ->
            val rest = state.map.filterNot { it.button == b && it.hold == hold }
            state.copy(editing = null, map = if (event.target == null || !ButtonTargets.valid(event.target)) rest else rest + ButtonMapping(b, hold, event.target))
        } ?: state
        else -> state
    }

    override fun handle(event: Any, before: RemoteButtonsState, after: RemoteButtonsState) {
        when (event) {
            RemoteButtonsIntent.Save -> if (after.changed || after.map.isNotEmpty()) scope.launch {
                attempt { buttons.save(tvId, after.map) }
                send(Loaded(after.copy(saved = after.map)))
                // The look goes again with the buttons in it (LookCarry); that page asks for the screen lock.
                effect(ManageEffect.Navigate("home/${tvId.value}/again"))
            }
            RemoteButtonsIntent.SetUpHelper -> effect(ManageEffect.Navigate("setup/${tvId.value}"))
            else -> {}
        }
    }

    private suspend fun load() {
        val tv = d.tv(tvId)
        val saved = attempt { buttons.load(tvId) }.getOrNull().orEmpty()
        val snap = attempt { d.snapshots.latest(tvId) }.getOrNull()
        val helperOn = d.homeHelper && snap != null && HomeHelper.state(snap) == HomeHelper.State.ON
        val launchable = d.access.live(tvId)?.let { s -> (d.prober as? LaunchableApps)?.let { q -> attempt { q.launchable(s) }.getOrNull() } }
        val pkgs = snap?.packages.orEmpty()
        val apps = (launchable ?: pkgs.filterValues { !it.system }.keys)
            .filter { it.value != ButtonTargets.NEW_HOME && pkgs[it]?.enabled?.name?.startsWith("DISABLED") != true }
            .map { TargetChoice(it.value, d.nameOf(it, pkgs[it]?.label)) }
            .sortedBy { it.name.lowercase() }
        send(Loaded(RemoteButtonsState(tvName = tv?.nickname.orEmpty(), loading = false, helperOn = helperOn, map = saved, saved = saved, apps = apps)))
    }
}
