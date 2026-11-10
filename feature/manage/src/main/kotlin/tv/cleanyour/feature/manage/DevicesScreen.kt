package tv.cleanyour.feature.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import tv.cleanyour.core.flows.DevicesIntent
import tv.cleanyour.core.flows.DevicesState
import tv.cleanyour.core.flows.KnownDevice
import tv.cleanyour.ui.design.component.Banner
import tv.cleanyour.ui.design.component.BannerTone
import tv.cleanyour.ui.design.component.CytIconButton
import tv.cleanyour.ui.design.component.CytTopBar
import tv.cleanyour.ui.design.component.Gap
import tv.cleanyour.ui.design.component.Lead
import tv.cleanyour.ui.design.component.ListDivider
import tv.cleanyour.ui.design.component.ListGroup
import tv.cleanyour.ui.design.component.ListRow
import tv.cleanyour.ui.design.component.ScreenScaffold
import tv.cleanyour.ui.design.component.ScreenTitle
import tv.cleanyour.ui.design.component.SecondaryButton
import tv.cleanyour.ui.design.component.Spinner
import tv.cleanyour.ui.design.component.TertiaryButton
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.Glow

// Devices: the TVs and boxes this phone has set up, and what the search finds on this Wi-Fi now
// (DevicesMachine). A dot says connected (green), online (blue) or not seen (grey); the words say it too.
@Composable
fun DevicesScreen(state: DevicesState, onIntent: (DevicesIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(Glow.Tv, modifier, topBar = {
        CytTopBar(onBack = onBack, trailing = {
            if (state.searching) Box(Modifier.padding(horizontal = 16.dp)) { Spinner() }
            else CytIconButton(CytIcons.Refresh, stringResource(R.string.devices_refresh_cd), { onIntent(DevicesIntent.Refresh) })
        })
    }) {
        ScreenTitle(stringResource(R.string.devices_title), Modifier.padding(top = 20.dp))
        Lead(stringResource(R.string.devices_intro), Modifier.padding(top = 12.dp))
        if (!state.onWifi) Banner(stringResource(R.string.devices_not_on_wifi), BannerTone.WARNING, Modifier.padding(top = 16.dp), icon = CytIcons.WifiOff)
        if (state.searching) LiveLine(stringResource(R.string.devices_searching))

        SectionLabel(stringResource(R.string.devices_known_title))
        if (state.known.isEmpty()) Body(stringResource(R.string.devices_known_empty))
        else ListGroup {
            state.known.forEachIndexed { i, dev ->
                if (i > 0) ListDivider()
                KnownRow(dev, state, onIntent)
            }
        }

        SectionLabel(stringResource(R.string.devices_found_title))
        if (state.found.isEmpty()) Body(stringResource(R.string.devices_found_empty))
        else ListGroup {
            state.found.forEachIndexed { i, dev ->
                if (i > 0) ListDivider()
                val model = dev.model ?: dev.host
                ListRow(dev.name,
                    subtitle = msg(if (dev.debuggingOpen == true) R.string.devices_new_sub_open else R.string.devices_new_sub_closed, "model" to model),
                    leading = { Dot(CytTheme.colors.primary) },
                    trailing = { TertiaryButton(stringResource(R.string.devices_set_up), { onIntent(DevicesIntent.SetUpNew) }) })
            }
        }
        SecondaryButton(stringResource(R.string.devices_add), { onIntent(DevicesIntent.SetUpNew) }, Modifier.padding(top = 20.dp))
        Gap(24.dp)
    }
}

@Composable
private fun KnownRow(dev: KnownDevice, state: DevicesState, onIntent: (DevicesIntent) -> Unit) {
    val c = CytTheme.colors
    val busy = state.busy == dev.id
    val status = stringResource(when {
        dev.connected -> R.string.devices_status_connected
        busy || dev.connecting -> R.string.devices_status_connecting
        dev.online -> R.string.devices_status_online
        else -> R.string.devices_status_offline
    })
    val line = msg(R.string.devices_line, "model" to dev.model, "status" to status) +
        if (dev.connected) " · " + stringResource(R.string.devices_tap_to_open) else ""
    val failed = if (state.failed == dev.id) stringResource(R.string.devices_failed) else null
    val title = dev.room?.let { "${dev.name}, $it" } ?: dev.name
    ListRow(
        title,
        subtitle = listOfNotNull(line, dev.host, failed).joinToString("\n"),
        subtitleColor = if (failed != null) c.danger else c.textTertiary,
        leading = { Dot(when { dev.connected -> c.success; dev.online -> c.primary; else -> c.outlineStrong }) },
        onClick = if (dev.connected && state.busy == null) ({ onIntent(DevicesIntent.Open(dev.id)) }) else null,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    busy -> Box(Modifier.padding(horizontal = 16.dp)) { Spinner() }
                    // The row itself opens a connected device, so one button fits beside a long name.
                    dev.connected -> TertiaryButton(stringResource(R.string.devices_disconnect), { onIntent(DevicesIntent.Disconnect(dev.id)) }, enabled = state.busy == null, quiet = true)
                    else -> TertiaryButton(stringResource(R.string.devices_connect), { onIntent(DevicesIntent.Connect(dev.id)) }, enabled = state.busy == null)
                }
            }
        },
    )
}

// A status dot; the words beside it carry the meaning for a screen reader.
@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(12.dp).background(color, CircleShape).clearAndSetSemantics { })
}
