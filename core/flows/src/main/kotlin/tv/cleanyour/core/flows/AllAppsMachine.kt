package tv.cleanyour.core.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Snapshot
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.planner.PlanRequest
import tv.cleanyour.core.planner.SwitchOffRule
import tv.cleanyour.core.probe.RunningApps

// "All apps": every package on the TV, system and user, with whether it is on and whether it has a
// process running right now, and a switch for each. A switch-off is the ordinary PlanRequest.SwitchOff
// and a switch-on the ordinary PlanRequest.PutBack, so every change goes through the planner and the
// engine like Switched off's: each change is journalled for Undo and the restore point, and nothing
// is ever uninstalled. Three kinds of row:
// - an ordinary app: a switch;
// - a protected app (the denylist and SwitchOffRule: Play services, a TV input, a setup wizard, a
//   home screen not in use...): in red with its reason, and a switch whose switch-off asks first and
//   then runs as an override (PlanRequest.SwitchOff with override = true);
// - an app the TV needs to run (SwitchOffRule.criticalReason: the framework, the system UI, Settings,
//   the remote, the Wi-Fi, the debugging service, persistent apps, the home screen in use): a lock
//   and its reason, never a switch.
data class AppRow(
    val pkg: PackageName,
    val name: String,
    val system: Boolean,
    val off: Boolean,
    val running: Boolean,
    val locked: String?,          // the TV needs it to run: why it can never be switched off, or null
    val warning: String? = null,  // protected: why it is normally kept on; it can go off after a confirmation
)

enum class AppFilter { ALL, RUNNING, OFF }

data class AllAppsState(
    val tvName: String = "",
    val all: List<AppRow> = emptyList(),
    val filter: AppFilter = AppFilter.ALL,
    val showSystem: Boolean = true,
    val query: String = "",
    val busy: PackageName? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val runningKnown: Boolean = false,  // the TV answered the process list on the last read
    val readAtMs: Long? = null,         // when the package list was read from the TV
    val failed: Boolean = false,        // no snapshot at all: the TV could not be read and none is stored
    val confirm: AppRow? = null,        // the protected app waiting for "Switch it off anyway"
) {
    // What the screen shows: the filter, the system toggle and the search, by name.
    val rows: List<AppRow> get() {
        val q = query.trim().lowercase()
        return all.filter { r ->
            (showSystem || !r.system) &&
                when (filter) { AppFilter.ALL -> true; AppFilter.RUNNING -> r.running; AppFilter.OFF -> r.off } &&
                (q.isEmpty() || r.name.lowercase().contains(q) || r.pkg.value.lowercase().contains(q))
        }
    }
    val total: Int get() = all.size
    val runningCount: Int get() = all.count { it.running }
    val offCount: Int get() = all.count { it.off }
    val systemCount: Int get() = all.count { it.system }
    val protectedCount: Int get() = all.count { it.warning != null }
    val criticalCount: Int get() = all.count { it.locked != null }
}

sealed interface AllAppsIntent {
    data class SetOff(val pkg: PackageName, val off: Boolean) : AllAppsIntent
    data object ConfirmOff : AllAppsIntent    // the confirmation's "Switch it off anyway"
    data object CancelOff : AllAppsIntent
    data class Filter(val filter: AppFilter) : AllAppsIntent
    data class ShowSystem(val on: Boolean) : AllAppsIntent
    data class Query(val text: String) : AllAppsIntent
    data object Refresh : AllAppsIntent
}

class AllAppsMachine(
    private val d: ManageDeps,
    scope: CoroutineScope,
    private val tvId: TvId,
) : Machine<AllAppsState, AllAppsIntent, ManageEffect>(AllAppsState(), scope) {

    private val writer = TvWriter(d)
    private data class Loaded(val all: List<AppRow>, val tvName: String, val runningKnown: Boolean, val readAtMs: Long?, val failed: Boolean)
    private data class BusyOn(val pkg: PackageName?)
    private data class Refreshing(val on: Boolean)
    private data class Flip(val pkg: PackageName, val off: Boolean)
    private data class AskConfirm(val row: AppRow)

    // What this screen changed since the last full read: the stored snapshot can still say the old
    // state until the next read, so these win over it, and the next write for such a package plans
    // from a fresh read (a stale plan would find nothing to do, or pause STALE).
    private val changed = HashMap<PackageName, Boolean>()
    private var running: Set<PackageName>? = null

    init { scope.launch { load(fromTv = true) } }

    override fun reduce(state: AllAppsState, event: Any): AllAppsState = when (event) {
        is Loaded -> state.copy(all = event.all, tvName = event.tvName, runningKnown = event.runningKnown, readAtMs = event.readAtMs,
            failed = event.failed, loading = false, refreshing = false)
        is BusyOn -> state.copy(busy = event.pkg)
        is AskConfirm -> state.copy(confirm = event.row)
        is Refreshing -> state.copy(refreshing = event.on)
        is Flip -> state.copy(all = state.all.map { if (it.pkg == event.pkg) it.copy(off = event.off) else it })
        AllAppsIntent.ConfirmOff, AllAppsIntent.CancelOff -> state.copy(confirm = null)
        is AllAppsIntent.Filter -> state.copy(filter = event.filter)
        is AllAppsIntent.ShowSystem -> state.copy(showSystem = event.on)
        is AllAppsIntent.Query -> state.copy(query = event.text.take(80))
        else -> state
    }

    override fun handle(event: Any, before: AllAppsState, after: AllAppsState) {
        when (event) {
            is AllAppsIntent.SetOff -> {
                if (before.busy != null || before.refreshing) return
                val row = before.all.firstOrNull { it.pkg == event.pkg } ?: return
                if (row.off == event.off) return
                if (event.off && row.locked != null) return
                // A protected app asks first (its reason in red), then goes off as an override.
                if (event.off && row.warning != null) { send(AskConfirm(row)); return }
                scope.launch { change(row, event.off) }
            }
            AllAppsIntent.ConfirmOff -> {
                val row = before.confirm ?: return
                if (before.busy != null || before.refreshing) return
                scope.launch { change(row, off = true) }
            }
            AllAppsIntent.Refresh -> if (before.busy == null && !before.refreshing) scope.launch { load(fromTv = true) }
            else -> {}
        }
    }

    private suspend fun change(row: AppRow, off: Boolean) {
        val pkg = row.pkg
        send(BusyOn(pkg))
        val request = if (off) PlanRequest.SwitchOff(setOf(pkg), override = row.warning != null) else PlanRequest.PutBack(pkg)
        val r = writer.write(tvId, request, fresh = pkg in changed)
        send(BusyOn(null))
        when (r) {
            is WriteResult.Done -> {
                fun touches(op: Op) = when (op) {
                    is Op.Disable -> op.pkg == pkg
                    is Op.Enable -> op.pkg == pkg
                    is Op.DefaultState -> op.pkg == pkg
                    else -> false
                }
                val planned = r.plan.steps.any { touches(it.op) }
                val skipped = r.finished.skipped.any { touches(it.op) }
                when {
                    planned && !skipped -> {
                        changed[pkg] = off
                        send(Flip(pkg, off))
                        effect(ManageEffect.Snackbar(if (off) SnackKind.APP_OFF else SnackKind.PUT_BACK, row.name, undo = true, pkg = pkg))
                    }
                    // Nothing to do: the TV already had it that way (an older read said otherwise).
                    !planned -> { changed[pkg] = off; send(Flip(pkg, off)) }
                    d.ledger.keptTvInputs(r.plan.id).isNotEmpty() -> effect(ManageEffect.Snackbar(SnackKind.KEPT_TV_INPUT, row.name, false))
                    else -> effect(ManageEffect.Snackbar(SnackKind.KEPT_ON, row.name, false))
                }
            }
            is WriteResult.Refused -> effect(ManageEffect.Snackbar(SnackKind.APP_REFUSED, row.name, false))
            WriteResult.GateCancelled, WriteResult.NoTv, is WriteResult.Paused -> {}   // Paused: the open-run card shows (O2)
            is WriteResult.Failed -> effect(ManageEffect.Snackbar(SnackKind.FAILED, row.name, false))
        }
    }

    // `fromTv`: connect (the debugging step if needed) and read the package list and the processes
    // now; when the TV cannot be reached, the last stored read is shown instead.
    private suspend fun load(fromTv: Boolean) {
        val tv = d.tv(tvId) ?: return send(Loaded(emptyList(), "", false, null, failed = true))
        send(Refreshing(true))
        var snapshot: Snapshot? = null
        if (fromTv) {
            val session = (d.access.session(tvId) as? AccessResult.Ok)?.session
            if (session != null) {
                snapshot = attempt { d.runner.freshSnapshot(session) }.getOrNull()
                running = (d.prober as? RunningApps)?.let { q -> attempt { q.runningPackages(session) }.getOrNull() }
                if (snapshot != null) changed.clear()
            }
        }
        val snap = snapshot ?: attempt { d.snapshots.latest(tvId) }.getOrNull()
            ?: return send(Loaded(emptyList(), tv.nickname, false, null, failed = true))
        val profile = d.profileOf(tv)
        val stock = profile?.stockLaunchers.orEmpty()
        val live = running
        val rows = snap.packages.map { (pkg, st) ->
            val off = changed[pkg] ?: (st.enabled == EnabledState.DISABLED || st.enabled == EnabledState.DISABLED_USER)
            // Needed to run: a lock, whatever its state (one that is off can still be switched back on).
            val critical = attempt { SwitchOffRule.criticalReason(pkg, st, snap) }.getOrNull()
            val guarded = if (critical != null) null else attempt { SwitchOffRule.reason(pkg, st, profile, snap, stock) }.getOrNull()
            AppRow(pkg, d.nameOf(pkg, st.label), st.system, off, live?.contains(pkg) == true, locked = critical, warning = guarded)
        }.sortedWith(compareBy<AppRow>({ it.name.lowercase() }, { it.pkg.value }))
        send(Loaded(rows, tv.nickname, live != null, snap.takenAtMs, failed = false))
    }
}
