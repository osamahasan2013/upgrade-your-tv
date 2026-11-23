package tv.cleanyour.core.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.probe.SpeedUp

// Upgrade Your TV 1.9.1: "Speed up the TV". Android's animations at half speed or off (the three animation scales,
// which Developer options holds), background apps ended to free memory (the way Android does it when short of it),
// and app caches trimmed. None of it switches anything off or loses a setting; Normal puts the animations back.
enum class AnimSpeed(val scale: Float) { NORMAL(1f), FAST(0.5f), OFF(0f) }

sealed interface SpeedNote {
    data object Done : SpeedNote
    data object MemoryFreed : SpeedNote
    data object CachesTrimmed : SpeedNote
    data object NoTv : SpeedNote
    data class Failed(val detail: String) : SpeedNote
}

data class SpeedState(
    val tvName: String = "",
    val loading: Boolean = true,
    val anim: AnimSpeed? = null,
    val busy: Boolean = false,
    val note: SpeedNote? = null,
)

sealed interface SpeedIntent {
    data class Animations(val speed: AnimSpeed) : SpeedIntent
    data object FreeMemory : SpeedIntent
    data object TrimCaches : SpeedIntent
    data object All : SpeedIntent          // half-speed animations, memory and caches in one tap
    data object DismissNote : SpeedIntent
    data object OpenAllApps : SpeedIntent
}

class SpeedMachine(
    private val d: ManageDeps,
    scope: CoroutineScope,
    private val tvId: TvId,
) : Machine<SpeedState, SpeedIntent, ManageEffect>(SpeedState(), scope) {

    private data class Update(val f: (SpeedState) -> SpeedState)

    init { scope.launch { load() } }

    override fun reduce(state: SpeedState, event: Any): SpeedState = when (event) {
        is Update -> event.f(state)
        SpeedIntent.DismissNote -> state.copy(note = null)
        else -> state
    }

    override fun handle(event: Any, before: SpeedState, after: SpeedState) {
        if (before.busy && event !is Update && event != SpeedIntent.DismissNote) return
        when (event) {
            is SpeedIntent.Animations -> scope.launch { work(SpeedNote.Done) { q, s -> q.setAnimationScale(s, event.speed.scale); send(Update { it.copy(anim = event.speed) }) } }
            SpeedIntent.FreeMemory -> scope.launch { work(SpeedNote.MemoryFreed) { q, s -> q.freeMemory(s) } }
            SpeedIntent.TrimCaches -> scope.launch { work(SpeedNote.CachesTrimmed) { q, s -> q.trimCaches(s) } }
            SpeedIntent.All -> scope.launch {
                work(SpeedNote.Done) { q, s ->
                    q.setAnimationScale(s, AnimSpeed.FAST.scale)
                    send(Update { it.copy(anim = AnimSpeed.FAST) })
                    q.trimCaches(s)
                    q.freeMemory(s)
                }
            }
            SpeedIntent.OpenAllApps -> effect(ManageEffect.Navigate("allapps/${tvId.value}"))
            else -> {}
        }
    }

    private suspend fun session() = (d.access.session(tvId) as? AccessResult.Ok)?.session

    private suspend fun load() {
        val tv = d.tv(tvId)
        send(Update { it.copy(tvName = tv?.nickname.orEmpty(), loading = true) })
        val s = session()
        val q = d.prober as? SpeedUp
        val scale = if (s != null && q != null) attempt { q.animationScale(s) }.getOrNull() else null
        val anim = scale?.let { v -> AnimSpeed.entries.minByOrNull { kotlin.math.abs(it.scale - v) } }
        send(Update { it.copy(loading = false, anim = anim, note = if (s == null) SpeedNote.NoTv else null) })
    }

    private suspend fun work(done: SpeedNote, block: suspend (SpeedUp, tv.cleanyour.core.session.TvSession) -> Unit) {
        val q = d.prober as? SpeedUp ?: return
        val s = session() ?: return send(Update { it.copy(note = SpeedNote.NoTv) })
        send(Update { it.copy(busy = true, note = null) })
        val r = attempt { block(q, s) }
        send(Update { it.copy(busy = false, note = r.exceptionOrNull()?.let { e -> SpeedNote.Failed(e.message ?: "failed") } ?: done) })
    }
}
