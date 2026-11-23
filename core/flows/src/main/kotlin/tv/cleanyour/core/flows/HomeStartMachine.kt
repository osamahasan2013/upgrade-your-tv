package tv.cleanyour.core.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.planner.HomeHelper
import tv.cleanyour.core.planner.SwitchOffRule
import tv.cleanyour.core.probe.HelperKeep

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
    val needsLook: Boolean = false,      // on the list, but the look on the TV lacks its keys: one send fixes it
    val droppedByTv: Boolean = false,    // set up before, and the TV took it off its list (power-off, a force stop)
    val working: Boolean = false,
    val failed: String? = null,
    val homeNow: String? = null,         // what the TV's Home answers to now
    val openAtStart: Boolean = false,
    val savedOpenAtStart: Boolean = false,
    val keeps: Boolean? = null,          // the launcher may put the helper back after power-off (null: not tried)
) {
    val changed: Boolean get() = openAtStart != savedOpenAtStart
}

sealed interface HomeStartIntent {
    data object TurnOnHelper : HomeStartIntent          // by itself: the accessibility entry, then the look again
    data object TurnOffHelper : HomeStartIntent
    data object OpenHomeScreen : HomeStartIntent
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
    private data class Kept(val ok: Boolean)
    private data class Working(val on: Boolean)
    private data class Failed(val why: String?)

    init { scope.launch { load() } }

    override fun reduce(state: HomeStartState, event: Any): HomeStartState = when (event) {
        is Loaded -> event.s
        is Kept -> state.copy(keeps = event.ok)
        is Working -> state.copy(working = event.on, failed = null)
        is Failed -> state.copy(working = false, failed = event.why)
        is HomeStartIntent.OpenAtStart -> state.copy(openAtStart = event.on)
        else -> state
    }

    override fun handle(event: Any, before: HomeStartState, after: HomeStartState) {
        when (event) {
            HomeStartIntent.TurnOnHelper -> if (!before.working && before.launcherOn) scope.launch { switch(on = true) }
            HomeStartIntent.TurnOffHelper -> if (!before.working) scope.launch { switch(on = false) }
            HomeStartIntent.OpenButtons -> effect(ManageEffect.Navigate("buttons/${tvId.value}"))
            HomeStartIntent.OpenHomeScreen -> effect(ManageEffect.Navigate("home/${tvId.value}"))
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

    // 1.10.0: the helper on or off from here, by itself: no Setup, no look chosen again. The launcher's entry on
    // the TV's accessibility list changes (HelperKeep), the TV is read again, and the look this TV already has
    // goes once more so the helper's keys are in it (or out of it): LookCarry writes them while the helper is
    // listed. The screen lock is asked, as for every change to the TV.
    private suspend fun switch(on: Boolean) {
        val tv = d.tv(tvId) ?: return
        val keep = d.prober as? HelperKeep ?: return send(Failed("this build cannot switch the helper"))
        d.gate.pass(tvId, tv.nickname) ?: return
        send(Working(true))
        val s = (d.access.session(tvId) as? AccessResult.Ok)?.session ?: return send(Failed("the TV could not be reached"))
        val ok = attempt { if (on) keep.helperOn(s) else keep.helperOff(s) }.getOrNull() == true
        if (!ok) return send(Failed(if (on) "the TV did not take the helper onto its accessibility list" else "the TV kept the helper on its accessibility list"))
        if (on) attempt { keep.keepHelper(s) }
        d.sessionEnd.touched(tvId)
        attempt { d.runner.freshSnapshot(s) }
        load()
        effect(ManageEffect.Navigate("home/${tvId.value}/again"))
    }

    private suspend fun load() {
        val tv = d.tv(tvId)
        // The TV as it is now when it is connected (the helper may have been dropped since the last read).
        val live = d.access.live(tvId)
        val snap = live?.let { s -> attempt { d.runner.freshSnapshot(s) }.getOrNull() } ?: attempt { d.snapshots.latest(tvId) }.getOrNull()
        val state = snap?.let { HomeHelper.state(it) }
        val helperOn = state == HomeHelper.State.ON || state == HomeHelper.State.LISTED_NO_REDIRECT
        val extras = attempt { store.load(tvId) }.getOrNull() ?: HomeExtras()
        val launcherOn = snap?.packages?.get(SwitchOffRule.OUR_LAUNCHER) != null
        send(Loaded(HomeStartState(tvName = tv?.nickname.orEmpty(), loading = false,
            launcherOn = launcherOn, helperOn = helperOn, needsLook = state == HomeHelper.State.LISTED_NO_REDIRECT,
            droppedByTv = state == HomeHelper.State.REDIRECT_ONLY || state == HomeHelper.State.LISTED_OFF,
            homeNow = snap?.home?.resolved?.let { "${it.pkg.value}/${it.cls}" },
            openAtStart = extras.openAtStart, savedOpenAtStart = extras.openAtStart)))
        // What lets the home screen put the helper back by itself after the TV was off (HelperKeep.keepHelper):
        // set quietly whenever this page opens with the TV there; a home screen installed again has lost them.
        val keep = d.prober as? HelperKeep
        if (keep != null && launcherOn) {
            val s = live ?: (d.access.session(tvId) as? AccessResult.Ok)?.session
            if (s != null) send(Kept(attempt { keep.keepHelper(s) }.getOrNull() == true))
        }
    }
}
