package tv.cleanyour.core.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.planner.HomeHelper
import tv.cleanyour.core.planner.SwitchOffRule

// Upgrade Your TV 1.9.3: "Home button & start", a page of its own. The Home button helper (RFC-HELPER-1) is what
// makes the remote's Home open the new home screen on a TV whose own home screen stays Home (TCL, Google TV), what
// the remote's other buttons go through (Remote buttons) and what brings the new home screen up at every power-on
// (power_on_home_always, HomeExtras.openAtStart). Here: whether it is on, why Setup does not offer it when it does
// not, the one way to switch it on (Setup with the helper chosen), and "Open when the TV starts".
data class HomeStartState(
    val tvName: String = "",
    val loading: Boolean = true,
    val launcherOn: Boolean = false,     // our launcher is on the TV
    val helperOn: Boolean = false,       // HomeKeyService listed and accessibility on
    val helperWhy: String? = null,       // why Setup does not offer it (HomeHelper.refusal), when it does not
    val homeNow: String? = null,         // what the TV's Home answers to now
    val openAtStart: Boolean = false,
    val savedOpenAtStart: Boolean = false,
) {
    val changed: Boolean get() = openAtStart != savedOpenAtStart
}

sealed interface HomeStartIntent {
    data object TurnOnHelper : HomeStartIntent          // Setup, with the helper chosen once it offers it
    data class OpenAtStart(val on: Boolean) : HomeStartIntent
    data object Save : HomeStartIntent                  // kept, then the look goes again with it
    data object OpenButtons : HomeStartIntent
}

class HomeStartMachine(
    private val d: ManageDeps,
    scope: CoroutineScope,
    private val tvId: TvId,
    private val store: HomeExtrasStore = HomeExtrasStore(d.documents),
) : Machine<HomeStartState, HomeStartIntent, ManageEffect>(HomeStartState(), scope) {

    private data class Loaded(val s: HomeStartState)

    init { scope.launch { load() } }

    override fun reduce(state: HomeStartState, event: Any): HomeStartState = when (event) {
        is Loaded -> event.s
        is HomeStartIntent.OpenAtStart -> state.copy(openAtStart = event.on)
        else -> state
    }

    override fun handle(event: Any, before: HomeStartState, after: HomeStartState) {
        when (event) {
            HomeStartIntent.TurnOnHelper -> effect(ManageEffect.Navigate("setup/${tvId.value}/helper"))
            HomeStartIntent.OpenButtons -> effect(ManageEffect.Navigate("buttons/${tvId.value}"))
            HomeStartIntent.Save -> if (after.changed) scope.launch {
                val extras = attempt { store.load(tvId) }.getOrNull() ?: HomeExtras()
                attempt { store.save(tvId, extras.copy(openAtStart = after.openAtStart)) }
                send(Loaded(after.copy(savedOpenAtStart = after.openAtStart)))
                // The look goes again with power_on_home_always in it (LookCarry); that page asks for the screen lock.
                effect(ManageEffect.Navigate("home/${tvId.value}/again"))
            }
            else -> {}
        }
    }

    private suspend fun load() {
        val tv = d.tv(tvId)
        val snap = attempt { d.snapshots.latest(tvId) }.getOrNull()
        val helperOn = d.homeHelper && snap != null && HomeHelper.state(snap) == HomeHelper.State.ON
        val stocks = tv?.let { attempt { d.profileOf(it) }.getOrNull() }?.stockLaunchers.orEmpty()
        val why = if (helperOn || snap == null) null else HomeHelper.refusal(snap, stocks, refusesOtherHomes = false, build = true)?.reason
        val extras = attempt { store.load(tvId) }.getOrNull() ?: HomeExtras()
        send(Loaded(HomeStartState(tvName = tv?.nickname.orEmpty(), loading = false,
            launcherOn = snap?.packages?.get(SwitchOffRule.OUR_LAUNCHER) != null, helperOn = helperOn, helperWhy = why,
            homeNow = snap?.home?.resolved?.let { "${it.pkg.value}/${it.cls}" },
            openAtStart = extras.openAtStart, savedOpenAtStart = extras.openAtStart)))
    }
}
