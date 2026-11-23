// CytBottomSheet takes the Material 3 sheet state, which is still marked experimental.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package tv.cleanyour.feature.manage

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.cleanyour.core.flows.ButtonTargets
import tv.cleanyour.core.flows.RemoteButton
import tv.cleanyour.core.flows.RemoteButtonsIntent
import tv.cleanyour.core.flows.RemoteButtonsState
import tv.cleanyour.ui.design.component.Banner
import tv.cleanyour.ui.design.component.BannerTone
import tv.cleanyour.ui.design.component.CytBottomSheet
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
import tv.cleanyour.ui.design.component.SheetHeader
import tv.cleanyour.ui.design.component.Spinner
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.Glow

// Remote buttons (RemoteButtonsMachine): each button the TV's remote has besides the ones every app needs,
// tapped and held, and what it opens. Saving sends the TV's look again with them in it.
@Composable
fun RemoteButtonsScreen(state: RemoteButtonsState, onIntent: (RemoteButtonsIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val save: (@Composable ColumnScope.() -> Unit)? = if (state.helperOn && state.changed) {
        { PrimaryButton(stringResource(R.string.buttons_save), { onIntent(RemoteButtonsIntent.Save) }, Modifier.fillMaxWidth()) }
    } else null
    ScreenScaffold(Glow.Tv, modifier, topBar = {
        CytTopBar(onBack = onBack, trailing = { if (state.loading) Box(Modifier.padding(horizontal = 16.dp)) { Spinner() } })
    }, bottomBar = save) {
        ScreenTitle(stringResource(R.string.buttons_title), Modifier.padding(top = 20.dp))
        Lead(stringResource(R.string.buttons_intro), Modifier.padding(top = 12.dp))
        if (state.loading) return@ScreenScaffold
        if (!state.helperOn) {
            Banner(stringResource(R.string.buttons_needs_helper), BannerTone.INFO, Modifier.padding(top = 16.dp))
            state.helperWhy?.let { Body(msg(R.string.buttons_helper_why, "why" to it), Modifier.padding(top = 12.dp), color = CytTheme.colors.textPrimary) }
            state.homeNow?.let { Body(msg(R.string.buttons_home_now, "home" to it), Modifier.padding(top = 6.dp)) }
            PrimaryButton(stringResource(R.string.buttons_turn_on_helper), { onIntent(RemoteButtonsIntent.SetUpHelper) }, Modifier.padding(top = 16.dp))
            return@ScreenScaffold
        }
        Body(stringResource(R.string.buttons_home_line), Modifier.padding(top = 12.dp))
        Overline(stringResource(R.string.buttons_group_tap), Modifier.padding(top = 20.dp, bottom = 8.dp))
        ListGroup {
            val tap = RemoteButton.entries.filterNot { it.holdOnly }
            tap.forEachIndexed { i, b ->
                ListRow(buttonName(b), value = targetName(state.targetOf(b, false), state), onClick = { onIntent(RemoteButtonsIntent.Edit(b, false)) })
                if (i < tap.lastIndex) ListDivider()
            }
        }
        Overline(stringResource(R.string.buttons_group_hold), Modifier.padding(top = 20.dp, bottom = 8.dp))
        ListGroup {
            RemoteButton.entries.forEachIndexed { i, b ->
                ListRow(buttonName(b), value = targetName(state.targetOf(b, true), state), onClick = { onIntent(RemoteButtonsIntent.Edit(b, true)) })
                if (i < RemoteButton.entries.lastIndex) ListDivider()
            }
        }
        Body(stringResource(R.string.buttons_note), Modifier.padding(top = 16.dp))
        Gap(24.dp)
    }
    state.editing?.let { (b, hold) ->
        CytBottomSheet({ onIntent(RemoteButtonsIntent.CloseEdit) }) { TargetPicker(b, hold, state, onIntent) }
    }
}

@Composable
internal fun ColumnScope.TargetPicker(b: RemoteButton, hold: Boolean, state: RemoteButtonsState, onIntent: (RemoteButtonsIntent) -> Unit) {
    SheetHeader(msg(if (hold) R.string.buttons_pick_hold else R.string.buttons_pick_tap, "button" to buttonName(b)))
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        val current = state.targetOf(b, hold)
        ListGroup {
            ListRow(stringResource(R.string.buttons_target_default), value = if (current == null) "✓" else null, onClick = { onIntent(RemoteButtonsIntent.Choose(null)) })
            ListDivider()
            ListRow(stringResource(R.string.buttons_target_new_home), value = if (current == ButtonTargets.NEW_HOME) "✓" else null,
                onClick = { onIntent(RemoteButtonsIntent.Choose(ButtonTargets.NEW_HOME)) })
            ButtonTargets.ACTIONS.forEach { a ->
                ListDivider()
                ListRow(actionName(a), value = if (current == a) "✓" else null, onClick = { onIntent(RemoteButtonsIntent.Choose(a)) })
            }
        }
        Overline(stringResource(R.string.buttons_group_apps), Modifier.padding(top = 16.dp, bottom = 8.dp))
        ListGroup {
            state.apps.forEachIndexed { i, a ->
                ListRow(a.name, subtitle = a.target, subtitleColor = CytTheme.colors.textTertiary, value = if (current == a.target) "✓" else null,
                    onClick = { onIntent(RemoteButtonsIntent.Choose(a.target)) })
                if (i < state.apps.lastIndex) ListDivider()
            }
        }
        Gap(24.dp)
    }
}

@Composable
private fun targetName(target: String?, state: RemoteButtonsState): String = when {
    target == null -> stringResource(R.string.buttons_value_default)
    target == ButtonTargets.NEW_HOME -> stringResource(R.string.buttons_target_new_home)
    target in ButtonTargets.ACTIONS -> actionName(target)
    else -> state.apps.firstOrNull { it.target == target }?.name ?: target
}

@Composable
private fun actionName(a: String): String = stringResource(when (a) {
    "inputs" -> R.string.buttons_action_inputs
    "settings" -> R.string.buttons_action_settings
    "wifi" -> R.string.buttons_action_wifi
    "mute" -> R.string.buttons_action_mute
    "sleep" -> R.string.buttons_action_sleep
    "screensaver" -> R.string.buttons_action_screensaver
    else -> R.string.buttons_action_recents
})

@Composable
private fun buttonName(b: RemoteButton): String = stringResource(when (b) {
    RemoteButton.RED -> R.string.buttons_red
    RemoteButton.GREEN -> R.string.buttons_green
    RemoteButton.YELLOW -> R.string.buttons_yellow
    RemoteButton.BLUE -> R.string.buttons_blue
    RemoteButton.INPUT -> R.string.buttons_input
    RemoteButton.LIVE_TV -> R.string.buttons_live_tv
    RemoteButton.GUIDE -> R.string.buttons_guide
    RemoteButton.INFO -> R.string.buttons_info
    RemoteButton.APPS -> R.string.buttons_apps
    RemoteButton.BOOKMARK -> R.string.buttons_bookmark
    RemoteButton.CAPTIONS -> R.string.buttons_captions
    RemoteButton.PLAY_PAUSE -> R.string.buttons_play_pause
    RemoteButton.HOME_HOLD -> R.string.buttons_home
})
