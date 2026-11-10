package tv.cleanyour.core.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.planner.SwitchOffRule
import tv.cleanyour.core.probe.VideoBackground

// Upgrade Your TV: "Video background". A short MP4 from the phone becomes the home screen's moving
// background: the Upgrade Your TV launcher (versionCode VIDEO_LAUNCHER_VERSION or later, signed with
// this app's key) plays wallpaper.mp4 from its theme folder, muted and looping, and only while Home is on
// screen. Removing it brings the look's own picture back. Both ask for the phone's screen lock.
enum class VideoHome { READY, NOT_INSTALLED, OLD_LAUNCHER, OTHER_KEY, NOT_HOME }

sealed interface VideoNote {
    data object Sent : VideoNote
    data object Removed : VideoNote
    data class Failed(val detail: String) : VideoNote
    data object TooBig : VideoNote
    data object NotMp4 : VideoNote
    data object Unreadable : VideoNote
    data object NoTv : VideoNote
}

data class VideoBackgroundState(
    val tvName: String = "",
    val loading: Boolean = true,
    val home: VideoHome = VideoHome.NOT_INSTALLED,
    val present: Boolean? = null,       // a video is set now (null: the TV could not say)
    val sending: Pair<Long, Long>? = null,   // sent, size
    val removing: Boolean = false,
    val note: VideoNote? = null,
) {
    val busy: Boolean get() = loading || sending != null || removing
}

sealed interface VideoBackgroundIntent {
    data object Refresh : VideoBackgroundIntent
    data class Picked(val ref: String) : VideoBackgroundIntent
    data object Remove : VideoBackgroundIntent
    data object DismissNote : VideoBackgroundIntent
    data object SetUpAgain : VideoBackgroundIntent   // a TV with the old launcher: Setup replaces it
}

class VideoBackgroundMachine(
    private val d: ManageDeps,
    scope: CoroutineScope,
    private val tvId: TvId,
    private val files: PhoneFiles = d.phoneFiles,
) : Machine<VideoBackgroundState, VideoBackgroundIntent, ManageEffect>(VideoBackgroundState(), scope) {

    private data class Update(val f: (VideoBackgroundState) -> VideoBackgroundState)

    init { scope.launch { load() } }

    override fun reduce(state: VideoBackgroundState, event: Any): VideoBackgroundState = when (event) {
        is Update -> event.f(state)
        VideoBackgroundIntent.DismissNote -> state.copy(note = null)
        else -> state
    }

    override fun handle(event: Any, before: VideoBackgroundState, after: VideoBackgroundState) {
        when (event) {
            VideoBackgroundIntent.Refresh -> if (!before.busy) scope.launch { load() }
            is VideoBackgroundIntent.Picked -> if (!before.busy && before.home == VideoHome.READY) scope.launch { sendVideo(event.ref) }
            VideoBackgroundIntent.Remove -> if (!before.busy && before.home == VideoHome.READY) scope.launch { remove() }
            VideoBackgroundIntent.SetUpAgain -> effect(ManageEffect.Navigate("setup/${tvId.value}"))
            else -> {}
        }
    }

    private suspend fun session() = (d.access.session(tvId) as? AccessResult.Ok)?.session

    private suspend fun load() {
        val tv = d.tv(tvId) ?: return send(Update { it.copy(loading = false) })
        send(Update { it.copy(loading = true, tvName = tv.nickname) })
        val s = session()
        val snap = s?.let { ss -> attempt { d.runner.freshSnapshot(ss) }.getOrNull() } ?: attempt { d.snapshots.latest(tvId) }.getOrNull()
        val pin = attempt { d.launcherPin.bundled() }.getOrNull()
        val st = snap?.packages?.get(SwitchOffRule.OUR_LAUNCHER)
        val home = when {
            st == null -> VideoHome.NOT_INSTALLED
            pin != null && st.signer != null && st.signer != pin.signer -> VideoHome.OTHER_KEY
            st.versionCode < VIDEO_LAUNCHER_VERSION -> VideoHome.OLD_LAUNCHER
            snap?.home?.resolved?.pkg != SwitchOffRule.OUR_LAUNCHER -> VideoHome.NOT_HOME
            else -> VideoHome.READY
        }
        val present = if (s != null && home == VideoHome.READY) (d.prober as? VideoBackground)?.let { q -> attempt { q.videoPresent(s) }.getOrNull() } else null
        send(Update { it.copy(loading = false, home = home, present = present) })
    }

    private suspend fun sendVideo(ref: String) {
        val tv = d.tv(tvId) ?: return note(VideoNote.NoTv)
        val f = files.describe(ref) ?: return note(VideoNote.Unreadable)
        if (!f.name.lowercase().endsWith(".mp4")) return note(VideoNote.NotMp4)
        if (f.size <= 0 || f.size > MAX_VIDEO_BYTES) return note(VideoNote.TooBig)
        d.gate.pass(tvId, tv.nickname) ?: return
        val s = session() ?: return note(VideoNote.NoTv)
        val q = d.prober as? VideoBackground ?: return note(VideoNote.Failed("this build cannot send videos"))
        val sdk = attempt { d.snapshots.latest(tvId) }.getOrNull()?.facts?.sdk ?: s.identity.sdk
        send(Update { it.copy(sending = 0L to f.size) })
        val r = attempt {
            q.sendVideo(s, sdk, { files.open(ref) ?: error("the video can no longer be read") }, f.size) { sent ->
                send(Update { it.copy(sending = sent to f.size) })
            }
        }
        send(Update { it.copy(sending = null) })
        if (r.isFailure) return note(VideoNote.Failed(r.exceptionOrNull()?.message ?: "failed"))
        send(Update { it.copy(present = true) })
        note(VideoNote.Sent)
    }

    private suspend fun remove() {
        val tv = d.tv(tvId) ?: return note(VideoNote.NoTv)
        d.gate.pass(tvId, tv.nickname) ?: return
        val s = session() ?: return note(VideoNote.NoTv)
        val q = d.prober as? VideoBackground ?: return
        send(Update { it.copy(removing = true) })
        val r = attempt { q.removeVideo(s) }
        send(Update { it.copy(removing = false) })
        if (r.isFailure) return note(VideoNote.Failed(r.exceptionOrNull()?.message ?: "failed"))
        send(Update { it.copy(present = false) })
        note(VideoNote.Removed)
    }

    private fun note(n: VideoNote) { send(Update { it.copy(note = n) }) }

    companion object {
        // The first Upgrade Your TV launcher with the video background (patches/launcher, 3.0-uytv1).
        const val VIDEO_LAUNCHER_VERSION = 1001L
        // A background is a short loop; the launcher skips a bigger file.
        const val MAX_VIDEO_BYTES = 200L * 1024 * 1024
    }
}
