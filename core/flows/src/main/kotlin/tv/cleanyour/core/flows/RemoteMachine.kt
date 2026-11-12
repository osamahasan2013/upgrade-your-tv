package tv.cleanyour.core.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.probe.LiveScreen
import tv.cleanyour.core.probe.ProbeScripts
import tv.cleanyour.core.probe.RemoteInput
import tv.cleanyour.core.session.TvSession

// Upgrade Your TV 1.7.0: the TV's remote on the phone. Each press goes to the TV as its own remote sends it
// (`input keyevent`), one after another in the order tapped; typed text goes in with `input text`. While the
// page is on screen the TV's picture is shown above the buttons and follows the TV's screen (LiveScreen).
// Nothing here changes the TV's settings, so no screen lock is asked.
object RemoteKeyCodes {
    const val POWER = 26
    const val HOME = 3
    const val BACK = 4
    const val MENU = 82
    const val UP = 19
    const val DOWN = 20
    const val LEFT = 21
    const val RIGHT = 22
    const val OK = 23
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val MUTE = 164
    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167
    const val PLAY_PAUSE = 85
    const val REWIND = 89
    const val FAST_FORWARD = 90
    const val SETTINGS = 176
    const val INPUT = 178
    const val RECENTS = 187
    const val DELETE = 67
    const val ENTER = 66
    val ALL: Set<Int> = setOf(POWER, HOME, BACK, MENU, UP, DOWN, LEFT, RIGHT, OK, VOLUME_UP, VOLUME_DOWN, MUTE, CHANNEL_UP,
        CHANNEL_DOWN, PLAY_PAUSE, REWIND, FAST_FORWARD, SETTINGS, INPUT, RECENTS, DELETE, ENTER)
}

sealed interface RemoteNote {
    data object NoTv : RemoteNote
    data object CannotType : RemoteNote   // letters outside the set `input text` takes (ProbeScripts.isSafeText)
    data class Failed(val detail: String) : RemoteNote
}

data class RemoteState(
    val tvName: String = "",
    val connected: Boolean = false,
    val connecting: Boolean = true,
    val frame: ByteArray? = null,
    val pressing: Int? = null,      // the key just sent, for the button's flash
    val typing: Boolean = false,
    val note: RemoteNote? = null,
) {
    override fun equals(other: Any?): Boolean = other is RemoteState && tvName == other.tvName && connected == other.connected &&
        connecting == other.connecting && frame === other.frame && pressing == other.pressing && typing == other.typing && note == other.note
    override fun hashCode(): Int = tvName.hashCode() * 31 + connected.hashCode()
}

sealed interface RemoteIntent {
    data class Press(val code: Int) : RemoteIntent
    data class Hold(val code: Int) : RemoteIntent
    data class Type(val text: String) : RemoteIntent
    data class Live(val on: Boolean) : RemoteIntent
    data object Reconnect : RemoteIntent
    data object DismissNote : RemoteIntent
}

class RemoteMachine(
    private val d: ManageDeps,
    scope: CoroutineScope,
    private val tvId: TvId,
) : Machine<RemoteState, RemoteIntent, ManageEffect>(RemoteState(), scope) {

    private data class Update(val f: (RemoteState) -> RemoteState)
    private sealed interface Send { data class Key(val code: Int, val long: Boolean) : Send; data class Text(val text: String) : Send }

    private val queue = Channel<Send>(Channel.UNLIMITED)
    private var liveJob: Job? = null
    // 1.8.3: a screenshot holds the connection for a second or more, so a press waited behind it. While the
    // person presses buttons the picture stops (the frame in progress is dropped) and comes back once they pause.
    private var liveWanted = false
    private var resumeJob: Job? = null

    init {
        scope.launch { connect() }
        scope.launch { for (s in queue) deliver(s) }
    }

    override fun reduce(state: RemoteState, event: Any): RemoteState = when (event) {
        is Update -> event.f(state)
        RemoteIntent.DismissNote -> state.copy(note = null)
        else -> state
    }

    override fun handle(event: Any, before: RemoteState, after: RemoteState) {
        when (event) {
            is RemoteIntent.Press -> if (event.code in RemoteKeyCodes.ALL) { pauseLive(); queue.trySend(Send.Key(event.code, false)) }
            is RemoteIntent.Hold -> if (event.code in RemoteKeyCodes.ALL) { pauseLive(); queue.trySend(Send.Key(event.code, true)) }
            is RemoteIntent.Type -> {
                val t = event.text.trim()
                if (t.isEmpty()) return
                if (!ProbeScripts.isSafeText(t)) send(Update { it.copy(note = RemoteNote.CannotType) })
                else queue.trySend(Send.Text(t))
            }
            is RemoteIntent.Live -> { liveWanted = event.on; if (event.on) startLive() else { resumeJob?.cancel(); stopLive() } }
            RemoteIntent.Reconnect -> if (!before.connecting) scope.launch { connect() }
            else -> {}
        }
    }

    private suspend fun connect() {
        val tv = d.tv(tvId)
        send(Update { it.copy(connecting = true, tvName = tv?.nickname.orEmpty()) })
        val ok = tv != null && d.access.session(tvId) is AccessResult.Ok
        send(Update { it.copy(connecting = false, connected = ok, note = if (ok) null else RemoteNote.NoTv) })
    }

    private suspend fun session(): TvSession? = d.access.live(tvId) ?: (d.access.session(tvId) as? AccessResult.Ok)?.session

    private suspend fun deliver(s: Send) {
        val input = d.prober as? RemoteInput ?: return
        val session = session() ?: return send(Update { it.copy(connected = false, note = RemoteNote.NoTv) })
        if (s is Send.Key) send(Update { it.copy(pressing = s.code) })
        if (s is Send.Text) send(Update { it.copy(typing = true) })
        val r = attempt {
            when (s) {
                is Send.Key -> input.key(session, s.code, s.long)
                is Send.Text -> input.text(session, s.text)
            }
        }
        send(Update { it.copy(pressing = null, typing = false, connected = true,
            note = r.exceptionOrNull()?.let { e -> RemoteNote.Failed(e.message ?: "failed") } ?: it.note) })
    }

    // The TV's screen, frame after frame, while the page is on screen.
    private fun startLive() {
        val q = d.prober as? LiveScreen ?: return
        if (liveJob?.isActive == true) return
        liveJob = scope.launch {
            var last: TvSession? = null
            try {
                while (true) {
                    val s = d.access.live(tvId)
                    if (s == null) { delay(LIVE_IDLE_MS); continue }
                    last = s
                    val png = attempt { q.frame(s) }.getOrNull()
                    if (png == null) { delay(LIVE_IDLE_MS); continue }
                    send(Update { it.copy(frame = png, connected = true) })
                    delay(LIVE_GAP_MS)
                }
            } finally {
                last?.let { s -> withContext(NonCancellable) { attempt { q.endLive(s) } } }
            }
        }
    }

    private fun stopLive() { liveJob?.cancel(); liveJob = null }

    private fun pauseLive() {
        if (!liveWanted) return
        stopLive()
        resumeJob?.cancel()
        resumeJob = scope.launch {
            delay(RESUME_AFTER_MS)
            if (liveWanted) startLive()
        }
    }

    companion object {
        const val LIVE_GAP_MS = 150L
        const val RESUME_AFTER_MS = 1_200L
        const val LIVE_IDLE_MS = 2_000L
    }
}
