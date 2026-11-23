package tv.cleanyour.feature.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.cleanyour.core.flows.AnimSpeed
import tv.cleanyour.core.flows.SpeedIntent
import tv.cleanyour.core.flows.SpeedNote
import tv.cleanyour.core.flows.SpeedState
import tv.cleanyour.ui.design.component.Banner
import tv.cleanyour.ui.design.component.BannerTone
import tv.cleanyour.ui.design.component.CytTopBar
import tv.cleanyour.ui.design.component.Gap
import tv.cleanyour.ui.design.component.Lead
import tv.cleanyour.ui.design.component.ListDivider
import tv.cleanyour.ui.design.component.ListGroup
import tv.cleanyour.ui.design.component.ListRow
import tv.cleanyour.ui.design.component.Overline
import tv.cleanyour.ui.design.component.PrimaryButton
import tv.cleanyour.ui.design.component.ScreenScaffold
import tv.cleanyour.ui.design.component.ScreenTitle
import tv.cleanyour.ui.design.component.SegmentOption
import tv.cleanyour.ui.design.component.SegmentedChoice
import tv.cleanyour.ui.design.component.Spinner
import tv.cleanyour.ui.design.theme.Glow

// Speed up the TV (SpeedMachine): one button for all of it, then each part on its own, and what else helps.
@Composable
fun SpeedScreen(state: SpeedState, onIntent: (SpeedIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(Glow.Tv, modifier, topBar = {
        CytTopBar(onBack = onBack, trailing = { if (state.loading || state.busy) Box(Modifier.padding(horizontal = 16.dp)) { Spinner() } })
    }) {
        ScreenTitle(stringResource(R.string.speed_title), Modifier.padding(top = 20.dp))
        Lead(stringResource(R.string.speed_intro), Modifier.padding(top = 12.dp))
        state.note?.let { n ->
            val (text, tone) = when (n) {
                SpeedNote.Done -> stringResource(R.string.speed_note_done) to BannerTone.SUCCESS
                SpeedNote.MemoryFreed -> stringResource(R.string.speed_note_memory) to BannerTone.SUCCESS
                SpeedNote.CachesTrimmed -> stringResource(R.string.speed_note_caches) to BannerTone.SUCCESS
                SpeedNote.NoTv -> stringResource(R.string.video_note_no_tv) to BannerTone.WARNING
                is SpeedNote.Failed -> msg(R.string.video_note_failed, "detail" to n.detail) to BannerTone.DANGER
            }
            Banner(text, tone, Modifier.padding(top = 16.dp), actionLabel = stringResource(R.string.storage_note_ok),
                onAction = { onIntent(SpeedIntent.DismissNote) })
        }
        PrimaryButton(stringResource(R.string.speed_all), { onIntent(SpeedIntent.All) }, Modifier.fillMaxWidth().padding(top = 18.dp),
            enabled = !state.busy && !state.loading)
        Body(stringResource(R.string.speed_all_sub), Modifier.padding(top = 8.dp))

        Overline(stringResource(R.string.speed_animations), Modifier.padding(top = 24.dp, bottom = 8.dp))
        SegmentedChoice(
            listOf(SegmentOption(AnimSpeed.NORMAL.name, stringResource(R.string.speed_anim_normal), stringResource(R.string.speed_anim_normal)),
                SegmentOption(AnimSpeed.FAST.name, stringResource(R.string.speed_anim_fast), stringResource(R.string.speed_anim_fast)),
                SegmentOption(AnimSpeed.OFF.name, stringResource(R.string.speed_anim_off), stringResource(R.string.speed_anim_off))),
            state.anim?.name, { onIntent(SpeedIntent.Animations(AnimSpeed.valueOf(it.key))) }, enabled = !state.busy && !state.loading,
        )
        Body(stringResource(R.string.speed_animations_sub), Modifier.padding(top = 8.dp))

        ListGroup(Modifier.padding(top = 20.dp)) {
            ListRow(stringResource(R.string.speed_memory), subtitle = stringResource(R.string.speed_memory_sub),
                onClick = { if (!state.busy) onIntent(SpeedIntent.FreeMemory) })
            ListDivider()
            ListRow(stringResource(R.string.speed_caches), subtitle = stringResource(R.string.speed_caches_sub),
                onClick = { if (!state.busy) onIntent(SpeedIntent.TrimCaches) })
            ListDivider()
            ListRow(stringResource(R.string.speed_apps), subtitle = stringResource(R.string.speed_apps_sub),
                onClick = { onIntent(SpeedIntent.OpenAllApps) })
        }
        Overline(stringResource(R.string.speed_tips), Modifier.padding(top = 24.dp, bottom = 8.dp))
        Body(stringResource(R.string.speed_tips_body))
        Gap(24.dp)
    }
}
