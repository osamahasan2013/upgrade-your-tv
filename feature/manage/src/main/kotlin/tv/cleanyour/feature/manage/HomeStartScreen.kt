package tv.cleanyour.feature.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.cleanyour.core.flows.HomeStartIntent
import tv.cleanyour.core.flows.HomeStartState
import tv.cleanyour.ui.design.component.Banner
import tv.cleanyour.ui.design.component.BannerTone
import tv.cleanyour.ui.design.component.CytTopBar
import tv.cleanyour.ui.design.component.Gap
import tv.cleanyour.ui.design.component.Lead
import tv.cleanyour.ui.design.component.ListGroup
import tv.cleanyour.ui.design.component.ListRow
import tv.cleanyour.ui.design.component.Overline
import tv.cleanyour.ui.design.component.PrimaryButton
import tv.cleanyour.ui.design.component.SecondaryButton
import tv.cleanyour.ui.design.component.ScreenScaffold
import tv.cleanyour.ui.design.component.ScreenTitle
import tv.cleanyour.ui.design.component.Spinner
import tv.cleanyour.ui.design.component.SwitchRow
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.Glow

// Home button & start (HomeStartMachine): the Home button helper, its one way on (Setup with it chosen), and
// "Open when the TV starts" in a place of its own, since both need the helper.
@Composable
fun HomeStartScreen(state: HomeStartState, onIntent: (HomeStartIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val save: (@Composable ColumnScope.() -> Unit)? = if (state.changed) {
        { PrimaryButton(stringResource(R.string.start_save), { onIntent(HomeStartIntent.Save) }, Modifier.fillMaxWidth()) }
    } else null
    ScreenScaffold(Glow.Tv, modifier, topBar = {
        CytTopBar(onBack = onBack, trailing = { if (state.loading) Box(Modifier.padding(horizontal = 16.dp)) { Spinner() } })
    }, bottomBar = save) {
        ScreenTitle(stringResource(R.string.start_title), Modifier.padding(top = 20.dp))
        Lead(stringResource(R.string.start_intro), Modifier.padding(top = 12.dp))
        if (state.loading) return@ScreenScaffold
        if (!state.launcherOn) {
            Banner(stringResource(R.string.extras_no_launcher), BannerTone.INFO, Modifier.padding(top = 16.dp))
        }

        Overline(stringResource(R.string.start_helper), Modifier.padding(top = 22.dp, bottom = 8.dp))
        state.failed?.let { Banner(msg(R.string.start_failed, "why" to it), BannerTone.DANGER, Modifier.padding(bottom = 12.dp)) }
        if (state.working) LiveLine(stringResource(R.string.start_working))
        if (state.helperOn) {
            Banner(stringResource(if (state.needsLook) R.string.start_helper_needs_look else R.string.start_helper_on),
                if (state.needsLook) BannerTone.WARNING else BannerTone.SUCCESS)
            if (state.needsLook) PrimaryButton(stringResource(R.string.start_send_again), { onIntent(HomeStartIntent.TurnOnHelper) },
                Modifier.fillMaxWidth().padding(top = 14.dp), enabled = !state.working && state.launcherOn, guardObscured = false)
            SecondaryButton(stringResource(R.string.start_turn_off), { onIntent(HomeStartIntent.TurnOffHelper) },
                Modifier.fillMaxWidth().padding(top = 12.dp), enabled = !state.working)
        } else {
            Body(stringResource(if (state.droppedByTv) R.string.start_helper_dropped else R.string.start_helper_off), color = CytTheme.colors.textPrimary)
            state.homeNow?.let { Body(msg(R.string.buttons_home_now, "home" to it), Modifier.padding(top = 6.dp)) }
            PrimaryButton(stringResource(R.string.start_turn_on), { onIntent(HomeStartIntent.TurnOnHelper) }, Modifier.fillMaxWidth().padding(top = 14.dp),
                enabled = state.launcherOn && !state.working, guardObscured = false)
            Body(stringResource(R.string.start_turn_on_sub), Modifier.padding(top = 8.dp))
            if (!state.launcherOn) SecondaryButton(stringResource(R.string.start_open_home), { onIntent(HomeStartIntent.OpenHomeScreen) },
                Modifier.fillMaxWidth().padding(top = 12.dp))
        }
        when (state.keeps) {
            true -> Body(stringResource(R.string.start_keeps), Modifier.padding(top = 12.dp))
            false -> Body(stringResource(R.string.start_keeps_not), Modifier.padding(top = 12.dp))
            null -> {}
        }
        Body(stringResource(R.string.start_button_mapper), Modifier.padding(top = 12.dp))

        Overline(stringResource(R.string.start_at_start), Modifier.padding(top = 22.dp, bottom = 8.dp))
        ListGroup {
            SwitchRow(stringResource(R.string.start_at_start), state.openAtStart, { onIntent(HomeStartIntent.OpenAtStart(it)) },
                subtitle = stringResource(R.string.start_at_start_sub), enabled = state.launcherOn)
        }
        if (!state.helperOn) Body(stringResource(R.string.start_at_start_needs), Modifier.padding(top = 8.dp))

        ListGroup(Modifier.padding(top = 22.dp)) {
            ListRow(stringResource(R.string.start_buttons), subtitle = stringResource(R.string.start_buttons_sub), onClick = { onIntent(HomeStartIntent.OpenButtons) })
        }
        Gap(24.dp)
    }
}
