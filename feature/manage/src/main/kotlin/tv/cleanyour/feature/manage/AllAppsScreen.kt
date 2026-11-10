package tv.cleanyour.feature.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tv.cleanyour.core.flows.AllAppsIntent
import tv.cleanyour.core.flows.AllAppsState
import tv.cleanyour.core.flows.AppFilter
import tv.cleanyour.core.flows.AppRow
import tv.cleanyour.ui.design.component.Banner
import tv.cleanyour.ui.design.component.BannerTone
import tv.cleanyour.ui.design.component.CytIcon
import tv.cleanyour.ui.design.component.ConfirmQuestion
import tv.cleanyour.ui.design.component.CytBottomSheet
import tv.cleanyour.ui.design.component.CytIconButton
import tv.cleanyour.ui.design.component.CytSwitch
import tv.cleanyour.ui.design.component.CytTextField
import tv.cleanyour.ui.design.component.CytTopBar
import tv.cleanyour.ui.design.component.Gap
import tv.cleanyour.ui.design.component.Lead
import tv.cleanyour.ui.design.component.ListDivider
import tv.cleanyour.ui.design.component.ListGroup
import tv.cleanyour.ui.design.component.ListRow
import tv.cleanyour.ui.design.component.ScreenScaffold
import tv.cleanyour.ui.design.component.ScreenTitle
import tv.cleanyour.ui.design.component.SegmentOption
import tv.cleanyour.ui.design.component.SegmentedChoice
import tv.cleanyour.ui.design.component.Spinner
import tv.cleanyour.ui.design.component.SwitchRow
import tv.cleanyour.ui.design.component.TertiaryButton
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.Glow

// The rows drawn at once; "Show more" adds the next page. A TV has 200 to 500 packages, and the
// scaffold's column is not lazy.
private const val PAGE = 60

// All apps: every package on the TV, system and user, with whether it runs now and a switch for
// each. A protected app is drawn in red with its reason and asks before it goes off; an app the TV
// needs to run shows a lock instead of a switch (AllAppsMachine).
@Composable
fun AllAppsScreen(state: AllAppsState, onIntent: (AllAppsIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    ScreenScaffold(Glow.Tv, modifier, topBar = {
        CytTopBar(onBack = onBack, trailing = {
            if (state.refreshing && !state.loading) Box(Modifier.padding(horizontal = 16.dp)) { Spinner() }
            else CytIconButton(CytIcons.Refresh, stringResource(R.string.allapps_refresh_cd), { onIntent(AllAppsIntent.Refresh) })
        })
    }) {
        ScreenTitle(stringResource(R.string.allapps_title), Modifier.padding(top = 20.dp))
        if (state.loading) {
            LiveLine(stringResource(R.string.allapps_reading))
            return@ScreenScaffold
        }
        if (state.failed) {
            Banner(stringResource(R.string.allapps_failed), BannerTone.WARNING, Modifier.padding(top = 16.dp),
                actionLabel = stringResource(R.string.allapps_try_again), onAction = { onIntent(AllAppsIntent.Refresh) })
            return@ScreenScaffold
        }
        Lead(stringResource(R.string.allapps_intro), Modifier.padding(top = 12.dp))
        Body(
            if (state.runningKnown) msg(R.string.allapps_counts, "total" to state.total, "running" to state.runningCount, "off" to state.offCount)
            else msg(R.string.allapps_counts_no_running, "total" to state.total, "off" to state.offCount),
            Modifier.padding(top = 8.dp),
        )
        state.readAtMs?.let { Body(msg(R.string.allapps_read_at, "time" to shortDateTime(it, locale)), Modifier.padding(top = 2.dp)) }
        if (!state.runningKnown) Body(stringResource(R.string.allapps_running_unknown), Modifier.padding(top = 2.dp))

        CytTextField(state.query, { onIntent(AllAppsIntent.Query(it)) }, stringResource(R.string.allapps_search), Modifier.padding(top = 16.dp))
        SegmentedChoice(
            options = listOf(
                SegmentOption(AppFilter.ALL.name, stringResource(R.string.allapps_filter_all), stringResource(R.string.allapps_filter_all_cd)),
                SegmentOption(AppFilter.RUNNING.name, stringResource(R.string.allapps_filter_running), stringResource(R.string.allapps_filter_running_cd)),
                SegmentOption(AppFilter.OFF.name, stringResource(R.string.allapps_filter_off), stringResource(R.string.allapps_filter_off_cd)),
            ),
            selectedKey = state.filter.name,
            onSelect = { o -> onIntent(AllAppsIntent.Filter(AppFilter.valueOf(o.key))) },
            modifier = Modifier.padding(top = 12.dp),
            groupLabel = stringResource(R.string.allapps_filter_label),
        )
        ListGroup(Modifier.padding(top = 12.dp)) {
            SwitchRow(stringResource(R.string.allapps_show_system), state.showSystem, { onIntent(AllAppsIntent.ShowSystem(it)) },
                subtitle = msg(R.string.allapps_show_system_sub, "count" to state.systemCount))
        }
        if (state.showSystem) Banner(stringResource(R.string.allapps_system_warning), BannerTone.INFO, Modifier.padding(top = 12.dp))
        if (state.protectedCount > 0 || state.criticalCount > 0)
            Banner(stringResource(R.string.allapps_legend), BannerTone.DANGER, Modifier.padding(top = 12.dp))

        val rows = state.rows
        // A new filter or search starts again at the first page.
        var shown by rememberSaveable(state.filter, state.showSystem, state.query) { mutableStateOf(PAGE) }
        if (rows.isEmpty()) {
            Lead(stringResource(R.string.allapps_empty), Modifier.padding(top = 20.dp))
        } else {
            ListGroup(Modifier.padding(top = 16.dp)) {
                rows.take(shown).forEachIndexed { i, r ->
                    if (i > 0) ListDivider()
                    AppLine(r, state.busy, onIntent)
                }
            }
            if (rows.size > shown) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    TertiaryButton(msg(R.string.allapps_show_more, "count" to minOf(PAGE, rows.size - shown)), { shown += PAGE })
                }
            }
        }
        Gap(24.dp)
    }
    state.confirm?.let { r -> ConfirmOffSheet(r, onIntent) }
}

// The question before a protected app goes off: its reason, and "Switch it off anyway".
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ConfirmOffSheet(r: AppRow, onIntent: (AllAppsIntent) -> Unit) {
    CytBottomSheet({ onIntent(AllAppsIntent.CancelOff) }) {
        ConfirmOffContent(r, onIntent)
    }
}

@Composable
internal fun androidx.compose.foundation.layout.ColumnScope.ConfirmOffContent(r: AppRow, onIntent: (AllAppsIntent) -> Unit) {
    ConfirmQuestion(
        msg(R.string.allapps_confirm_title, "name" to r.name),
        msg(R.string.allapps_confirm_body, "reason" to (r.warning ?: "")),
        stringResource(R.string.allapps_confirm_button), { onIntent(AllAppsIntent.ConfirmOff) },
        stringResource(R.string.allapps_confirm_cancel), { onIntent(AllAppsIntent.CancelOff) },
    )
}

@Composable
private fun AppLine(r: AppRow, busy: tv.cleanyour.core.model.PackageName?, onIntent: (AllAppsIntent) -> Unit) {
    val c = CytTheme.colors
    val tags = listOfNotNull(
        stringResource(if (r.system) R.string.allapps_tag_system else R.string.allapps_tag_user),
        if (r.running) stringResource(R.string.allapps_tag_running) else null,
        if (r.off) stringResource(R.string.allapps_tag_off) else null,
    ).joinToString(" · ")
    // `locked` and `warning` are properties of another module's class: no smart cast, so let.
    val why = r.locked?.let { msg(R.string.allapps_locked, "reason" to it) }
        ?: r.warning?.let { msg(R.string.allapps_protected, "reason" to it) }
    val subtitle = listOfNotNull(r.pkg.value, tags, why).joinToString("\n")
    val stateWord = stringResource(if (r.off) R.string.allapps_state_off else R.string.allapps_state_on)
    val cd = msg(R.string.allapps_switch_cd, "name" to r.name, "state" to stateWord)
    val red = r.warning != null
    ListRow(
        r.name,
        subtitle = subtitle,
        subtitleColor = when {
            red -> c.danger
            r.off -> c.warning
            else -> c.textTertiary
        },
        titleColor = if (red) c.danger else c.textPrimary,
        trailing = {
            when {
                busy == r.pkg -> Box(Modifier.padding(horizontal = 16.dp)) { Spinner() }
                // Needed to run and on: never a switch. One that is off can still be switched back on.
                r.locked != null && !r.off -> CytIcon(CytIcons.Lock, null, Modifier.padding(horizontal = 12.dp), tint = c.textTertiary)
                else -> CytSwitch(
                    checked = !r.off,
                    onCheckedChange = { on -> onIntent(AllAppsIntent.SetOff(r.pkg, off = !on)) },
                    modifier = Modifier.semantics { contentDescription = cd },
                    enabled = busy == null,
                )
            }
        },
    )
}
