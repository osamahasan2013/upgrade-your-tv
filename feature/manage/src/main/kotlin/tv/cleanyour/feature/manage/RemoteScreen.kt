package tv.cleanyour.feature.manage

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.cleanyour.core.flows.RemoteIntent
import tv.cleanyour.core.flows.RemoteKeyCodes as K
import tv.cleanyour.core.flows.RemoteNote
import tv.cleanyour.core.flows.RemoteState
import tv.cleanyour.ui.design.component.Banner
import tv.cleanyour.ui.design.component.BannerTone
import tv.cleanyour.ui.design.component.CytIcon
import tv.cleanyour.ui.design.component.CytTextField
import tv.cleanyour.ui.design.component.CytTopBar
import tv.cleanyour.ui.design.component.Gap
import tv.cleanyour.ui.design.component.ScreenScaffold
import tv.cleanyour.ui.design.component.ScreenTitle
import tv.cleanyour.ui.design.component.SecondaryButton
import tv.cleanyour.ui.design.component.Spinner
import tv.cleanyour.ui.design.component.TvThumbnail
import tv.cleanyour.ui.design.component.rememberPng
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.Glow

// The remote on the phone (RemoteMachine): the TV's picture, following its screen while the page is open, then
// the remote's buttons in glass. A tap sends the button once; holding sends a long press (the TV's own menus).
// `onButtons` opens the page that changes what the remote's buttons do on the TV (Remote buttons).
@Composable
fun RemoteScreen(state: RemoteState, onIntent: (RemoteIntent) -> Unit, onBack: () -> Unit, onButtons: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(Glow.Tv, modifier, topBar = {
        CytTopBar(onBack = onBack, trailing = {
            if (state.connecting) Box(Modifier.padding(horizontal = 16.dp)) { Spinner() }
        })
    }) {
        ScreenTitle(stringResource(R.string.remote_title), Modifier.padding(top = 12.dp))
        if (state.tvName.isNotEmpty()) Body(state.tvName, Modifier.padding(top = 4.dp))
        state.note?.let { n ->
            val text = when (n) {
                RemoteNote.NoTv -> stringResource(R.string.remote_note_no_tv)
                RemoteNote.CannotType -> stringResource(R.string.remote_note_cannot_type)
                is RemoteNote.Failed -> msg(R.string.remote_note_failed, "detail" to n.detail)
            }
            Banner(text, BannerTone.WARNING, Modifier.padding(top = 12.dp),
                actionLabel = stringResource(if (n == RemoteNote.NoTv) R.string.remote_reconnect else R.string.storage_note_ok),
                onAction = { onIntent(if (n == RemoteNote.NoTv) RemoteIntent.Reconnect else RemoteIntent.DismissNote) })
        }
        TvThumbnail(rememberPng(state.frame), msg(R.string.manage_thumbnail_cd, "tv" to state.tvName), Modifier.padding(top = 14.dp))

        val press: (Int) -> Unit = { onIntent(RemoteIntent.Press(it)) }
        val hold: (Int) -> Unit = { onIntent(RemoteIntent.Hold(it)) }
        val p = state.pressing

        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Key(K.POWER, p, press, hold, stringResource(R.string.remote_power), text = "⏻", danger = true)
            Key(K.INPUT, p, press, hold, stringResource(R.string.remote_input), text = stringResource(R.string.remote_input_short), wide = true)
            Key(K.SETTINGS, p, press, hold, stringResource(R.string.remote_settings), icon = CytIcons.Settings)
        }

        // The D-pad: a glass ring with the four directions and OK in the middle.
        // Left is the TV's left whatever the phone's language: the pad is laid out left to right.
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Box(Modifier.fillMaxWidth().padding(top = 22.dp), contentAlignment = Alignment.Center) {
            val c = CytTheme.colors
            Box(Modifier.size(236.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(c.glassFillTop, c.glassFillBottom)))
                .border(1.dp, c.rim, CircleShape)) {
                Key(K.UP, p, press, hold, stringResource(R.string.remote_up), icon = CytIcons.ChevronUp, flat = true, modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
                Key(K.DOWN, p, press, hold, stringResource(R.string.remote_down), icon = CytIcons.ChevronDown, flat = true, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
                Key(K.LEFT, p, press, hold, stringResource(R.string.remote_left), icon = CytIcons.ChevronForward, iconTurn = 180f, flat = true, modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp))
                Key(K.RIGHT, p, press, hold, stringResource(R.string.remote_right), icon = CytIcons.ChevronForward, flat = true, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp))
                Key(K.OK, p, press, hold, stringResource(R.string.remote_ok), text = "OK", size = 92.dp, accent = true, modifier = Modifier.align(Alignment.Center))
            }
        }
        }

        Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Key(K.BACK, p, press, hold, stringResource(R.string.remote_back), icon = CytIcons.Back)
            Key(K.HOME, p, press, hold, stringResource(R.string.remote_home), icon = CytIcons.Home)
            Key(K.MENU, p, press, hold, stringResource(R.string.remote_menu), text = "☰")
            Key(K.RECENTS, p, press, hold, stringResource(R.string.remote_recents), text = "▢")
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Pair(K.VOLUME_DOWN to K.VOLUME_UP, stringResource(R.string.remote_volume)).let { (keys, label) -> Rocker(keys.first, keys.second, label, p, press, hold) }
            Key(K.MUTE, p, press, hold, stringResource(R.string.remote_mute), text = "🔇")
            Pair(K.CHANNEL_DOWN to K.CHANNEL_UP, stringResource(R.string.remote_channel)).let { (keys, label) -> Rocker(keys.first, keys.second, label, p, press, hold) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Key(K.REWIND, p, press, hold, stringResource(R.string.remote_rewind), text = "⏪")
            Key(K.PLAY_PAUSE, p, press, hold, stringResource(R.string.remote_play_pause), text = "⏯", wide = true)
            Key(K.FAST_FORWARD, p, press, hold, stringResource(R.string.remote_forward), text = "⏩")
        }

        // Typing: what the TV's on-screen keyboard is waiting for, sent in one go.
        var typed by rememberSaveable { mutableStateOf("") }
        CytTextField(typed, { typed = it }, stringResource(R.string.remote_type_label), Modifier.fillMaxWidth().padding(top = 24.dp),
            supporting = stringResource(R.string.remote_type_hint))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(stringResource(if (state.typing) R.string.remote_typing else R.string.remote_type_send),
                { onIntent(RemoteIntent.Type(typed)); typed = "" }, Modifier.weight(1f), enabled = typed.isNotBlank() && !state.typing)
            Key(K.DELETE, p, press, hold, stringResource(R.string.remote_delete), text = "⌫")
            Key(K.ENTER, p, press, hold, stringResource(R.string.remote_enter), text = "↵")
        }

        SecondaryButton(stringResource(R.string.remote_buttons_open), onButtons, Modifier.fillMaxWidth().padding(top = 24.dp))
        Gap(28.dp)
    }
}

// Volume or channel: one pill, minus below and plus above, its name in the middle.
@Composable
private fun Rocker(down: Int, up: Int, label: String, pressing: Int?, press: (Int) -> Unit, hold: (Int) -> Unit) {
    val c = CytTheme.colors
    val shape = RoundedCornerShape(36.dp)
    androidx.compose.foundation.layout.Column(
        Modifier.clip(shape).background(Brush.verticalGradient(listOf(c.glassFillTop, c.glassFillBottom))).border(1.dp, c.rim, shape).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Key(up, pressing, press, hold, "$label +", icon = CytIcons.Plus, flat = true)
        Text(label, color = c.textTertiary, style = CytTheme.type.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
        Key(down, pressing, press, hold, "$label −", icon = CytIcons.Minus, flat = true)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(
    code: Int, pressing: Int?, press: (Int) -> Unit, hold: (Int) -> Unit, description: String,
    modifier: Modifier = Modifier, icon: ImageVector? = null, text: String? = null, iconTurn: Float = 0f,
    size: Dp = 60.dp, wide: Boolean = false, flat: Boolean = false, accent: Boolean = false, danger: Boolean = false,
) {
    val c = CytTheme.colors
    val haptics = LocalHapticFeedback.current
    val shape: Shape = if (wide) RoundedCornerShape(size / 2) else CircleShape
    val lit = pressing == code
    val fill = when {
        lit -> Brush.verticalGradient(listOf(c.primary.copy(alpha = 0.55f), c.primary.copy(alpha = 0.35f)))
        accent -> Brush.verticalGradient(listOf(c.highlightFillTop, c.highlightFillBottom))
        flat -> Brush.verticalGradient(listOf(c.glassFillTop.copy(alpha = 0f), c.glassFillBottom.copy(alpha = 0f)))
        else -> Brush.verticalGradient(listOf(c.glassFillTop, c.glassFillBottom))
    }
    val ink = if (danger) c.danger else c.textPrimary
    val width = if (wide) size * 2 else size
    Box(
        modifier.size(width, size).clip(shape).background(fill)
            .then(if (flat && !lit) Modifier else Modifier.border(1.dp, if (accent) c.highlightRim else c.rim, shape))
            .semantics { contentDescription = description; role = Role.Button }
            .combinedClickable(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); press(code) },
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); hold(code) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon != null -> CytIcon(icon, null, Modifier.size(26.dp).rotate(iconTurn), tint = ink)
            text != null -> Text(text, color = ink, fontSize = if (text.length > 3) 14.sp else 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
