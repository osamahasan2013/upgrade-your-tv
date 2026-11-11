package tv.cleanyour.feature.manage

import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.cleanyour.core.flows.VideoBackgroundIntent
import tv.cleanyour.core.flows.VideoBackgroundState
import tv.cleanyour.core.flows.VideoHome
import tv.cleanyour.core.flows.VideoNote
import tv.cleanyour.core.flows.plays
import tv.cleanyour.ui.design.component.Banner
import tv.cleanyour.ui.design.component.BannerTone
import tv.cleanyour.ui.design.component.CytIconButton
import tv.cleanyour.ui.design.component.CytTopBar
import tv.cleanyour.ui.design.component.Gap
import tv.cleanyour.ui.design.component.Lead
import tv.cleanyour.ui.design.component.PrimaryButton
import tv.cleanyour.ui.design.component.ProgressBar
import tv.cleanyour.ui.design.component.ScreenScaffold
import tv.cleanyour.ui.design.component.ScreenTitle
import tv.cleanyour.ui.design.component.SecondaryButton
import tv.cleanyour.ui.design.component.Spinner
import tv.cleanyour.ui.design.component.TertiaryButton
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.Glow

// Video background: a short MP4 from the phone as the home screen's moving background
// (VideoBackgroundMachine). The app opens the system's file picker through `onPickVideo`.
@Composable
fun VideoBackgroundScreen(state: VideoBackgroundState, onIntent: (VideoBackgroundIntent) -> Unit, onBack: () -> Unit, onPickVideo: () -> Unit,
    modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun bytes(n: Long): String = Formatter.formatShortFileSize(context, n)
    ScreenScaffold(Glow.Tv, modifier, topBar = {
        CytTopBar(onBack = onBack, trailing = {
            if (state.loading) Box(Modifier.padding(horizontal = 16.dp)) { Spinner() }
            else CytIconButton(CytIcons.Refresh, stringResource(R.string.storage_refresh_cd), { onIntent(VideoBackgroundIntent.Refresh) })
        })
    }) {
        ScreenTitle(stringResource(R.string.video_title), Modifier.padding(top = 20.dp))
        Lead(stringResource(R.string.video_intro), Modifier.padding(top = 12.dp))
        if (state.loading) {
            LiveLine(stringResource(R.string.video_reading))
            return@ScreenScaffold
        }
        state.note?.let { n ->
            val (text, tone) = when (n) {
                VideoNote.Sent -> stringResource(R.string.video_note_sent) to BannerTone.SUCCESS
                VideoNote.Removed -> stringResource(R.string.video_note_removed) to BannerTone.SUCCESS
                VideoNote.InstalledAsApp -> stringResource(R.string.video_note_installed_app) to BannerTone.SUCCESS
                VideoNote.Opened -> stringResource(R.string.video_note_opened) to BannerTone.SUCCESS
                is VideoNote.Failed -> msg(R.string.video_note_failed, "detail" to n.detail) to BannerTone.DANGER
                VideoNote.TooBig -> stringResource(R.string.video_note_too_big) to BannerTone.WARNING
                VideoNote.NotMp4 -> stringResource(R.string.video_note_not_mp4) to BannerTone.WARNING
                VideoNote.Unreadable -> stringResource(R.string.video_note_unreadable) to BannerTone.WARNING
                VideoNote.NoTv -> stringResource(R.string.video_note_no_tv) to BannerTone.WARNING
            }
            Banner(text, tone, Modifier.padding(top = 16.dp), actionLabel = stringResource(R.string.storage_note_ok),
                onAction = { onIntent(VideoBackgroundIntent.DismissNote) })
        }
        val installing = state.installing
        if (installing != null) {
            LiveLine(msg(R.string.video_installing, "sent" to bytes(installing.first), "size" to bytes(installing.second)))
            ProgressBar(if (installing.second > 0) installing.first.toFloat() / installing.second else 0f, Modifier.fillMaxWidth().padding(top = 10.dp))
            return@ScreenScaffold
        }
        if (!state.home.plays) {
            val why = when (state.home) {
                VideoHome.OLD_LAUNCHER -> R.string.video_old_launcher
                VideoHome.OTHER_KEY -> R.string.video_other_key
                else -> R.string.video_not_installed
            }
            Banner(stringResource(why), BannerTone.INFO, Modifier.padding(top = 16.dp))
            PrimaryButton(stringResource(R.string.video_set_up_again), { onIntent(VideoBackgroundIntent.SetUpAgain) }, Modifier.padding(top = 16.dp))
            // A TV that keeps its own home screen: the Home screen page can put the new one on as an app.
            if (state.home != VideoHome.OTHER_KEY) {
                Body(stringResource(R.string.video_as_app_offer), Modifier.padding(top = 20.dp))
                SecondaryButton(stringResource(R.string.video_open_home_page), { onIntent(VideoBackgroundIntent.OpenHomeScreen) }, Modifier.padding(top = 12.dp))
            }
            return@ScreenScaffold
        }
        if (state.home == VideoHome.AS_APP) Banner(stringResource(R.string.video_as_app), BannerTone.INFO, Modifier.padding(top = 16.dp))
        Body(stringResource(when (state.present) {
            true -> R.string.video_ready_on
            false -> R.string.video_ready_off
            null -> R.string.video_unknown
        }), Modifier.padding(top = 16.dp))
        val sending = state.sending
        when {
            sending != null -> {
                LiveLine(msg(R.string.video_sending, "sent" to bytes(sending.first), "size" to bytes(sending.second)))
                ProgressBar(if (sending.second > 0) sending.first.toFloat() / sending.second else 0f, Modifier.fillMaxWidth().padding(top = 10.dp))
            }
            state.removing -> LiveLine(stringResource(R.string.video_removing))
            state.opening -> LiveLine(stringResource(R.string.video_opening))
            else -> {
                PrimaryButton(stringResource(if (state.present == true) R.string.video_replace else R.string.video_choose), onPickVideo,
                    Modifier.padding(top = 16.dp), enabled = !state.busy)
                if (state.present == true) SecondaryButton(stringResource(R.string.video_remove), { onIntent(VideoBackgroundIntent.Remove) },
                    Modifier.padding(top = 12.dp), enabled = !state.busy)
                if (state.home == VideoHome.AS_APP) TertiaryButton(stringResource(R.string.video_open_on_tv), { onIntent(VideoBackgroundIntent.OpenOnTv) },
                    Modifier.padding(top = 12.dp), enabled = !state.busy)
            }
        }
        Gap(24.dp)
    }
}
