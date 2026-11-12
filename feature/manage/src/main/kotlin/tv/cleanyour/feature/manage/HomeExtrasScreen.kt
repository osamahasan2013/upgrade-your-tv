package tv.cleanyour.feature.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.cleanyour.core.flows.HomeExtras
import tv.cleanyour.core.flows.HomeExtrasIntent
import tv.cleanyour.core.flows.HomeExtrasState
import tv.cleanyour.core.flows.NameSize
import tv.cleanyour.core.flows.HomeTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import tv.cleanyour.ui.design.component.Banner
import tv.cleanyour.ui.design.component.BannerTone
import tv.cleanyour.ui.design.component.CytTopBar
import tv.cleanyour.ui.design.component.Gap
import tv.cleanyour.ui.design.component.Lead
import tv.cleanyour.ui.design.component.ListDivider
import tv.cleanyour.ui.design.component.ListGroup
import tv.cleanyour.ui.design.component.Overline
import tv.cleanyour.ui.design.component.PrimaryButton
import tv.cleanyour.ui.design.component.ScreenScaffold
import tv.cleanyour.ui.design.component.ScreenTitle
import tv.cleanyour.ui.design.component.SecondaryButton
import tv.cleanyour.ui.design.component.SegmentOption
import tv.cleanyour.ui.design.component.SegmentedChoice
import tv.cleanyour.ui.design.component.Spinner
import tv.cleanyour.ui.design.component.SwitchRow
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.Glow

// The dot-matrix type the launcher uses (UytvHome), the same font file, so the preview shows it as the TV will.
private val Dots = FontFamily(Font(R.font.uytv_dots))

// Home screen layout (HomeExtrasMachine): the theme, type, where the apps go, names, the dock and the Continue card,
// with a preview of the TV's home screen that follows each choice. Saving sends the TV's look again with them.
// 1.9.3: the Home screen page shows the same section (HomeExtrasSection) under its dock and look, so there is one
// place for all of it; this page stays for the older route.
@Composable
fun HomeExtrasScreen(state: HomeExtrasState, onIntent: (HomeExtrasIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(Glow.Tv, modifier, topBar = {
        CytTopBar(onBack = onBack, trailing = { if (state.loading) Box(Modifier.padding(horizontal = 16.dp)) { Spinner() } })
    }) {
        ScreenTitle(stringResource(R.string.extras_title), Modifier.padding(top = 20.dp))
        HomeExtrasSection(state, onIntent)
        Gap(24.dp)
    }
}

// The layout section: one theme picker, and every part of the theme changeable after it (a theme only sets the
// parts out to start with). Its Save sends the look again, so the dock and the look above go with it.
@Composable
fun ColumnScope.HomeExtrasSection(state: HomeExtrasState, onIntent: (HomeExtrasIntent) -> Unit, inPage: Boolean = false) {
    if (inPage) ScreenTitle(stringResource(R.string.extras_section), Modifier.padding(top = 32.dp))
    Lead(stringResource(if (inPage) R.string.extras_intro_page else R.string.extras_intro), Modifier.padding(top = 12.dp))
    if (state.loading) return
    HomePreview(state.extras, Modifier.padding(top = 18.dp))
    if (!state.launcherOn) {
        Banner(stringResource(R.string.extras_no_launcher), BannerTone.INFO, Modifier.padding(top = 16.dp))
        if (!inPage) PrimaryButton(stringResource(R.string.video_set_up_again), { onIntent(HomeExtrasIntent.SetUpAgain) }, Modifier.padding(top = 16.dp))
        return
    }
    if (state.launcherOld) Banner(stringResource(R.string.extras_old_launcher), BannerTone.INFO, Modifier.padding(top = 16.dp))

    Overline(stringResource(R.string.extras_theme), Modifier.padding(top = 22.dp, bottom = 8.dp))
    ThemePicker(state.extras.theme) { onIntent(HomeExtrasIntent.Theme(it)) }
    if (state.extras.theme != HomeTheme.NONE) {
        Body(stringResource(R.string.extras_theme_parts), Modifier.padding(top = 10.dp))
        ListGroup(Modifier.padding(top = 10.dp)) {
            SwitchRow(stringResource(R.string.extras_theme_picture), state.extras.themePicture, { onIntent(HomeExtrasIntent.ThemePicture(it)) },
                subtitle = stringResource(R.string.extras_theme_picture_sub))
        }
    }
    Overline(stringResource(R.string.extras_type), Modifier.padding(top = 22.dp, bottom = 8.dp))
    SegmentedChoice(
        listOf(SegmentOption("plain", stringResource(R.string.extras_type_plain), stringResource(R.string.extras_type_plain)),
            SegmentOption("signal", stringResource(R.string.extras_type_signal), stringResource(R.string.extras_type_signal_cd))),
        if (state.extras.signal) "signal" else "plain", { onIntent(HomeExtrasIntent.Signal(it.key == "signal")) },
    )
    Overline(stringResource(R.string.extras_layout), Modifier.padding(top = 20.dp, bottom = 8.dp))
    SegmentedChoice(
        listOf(SegmentOption("row", stringResource(R.string.extras_layout_row), stringResource(R.string.extras_layout_row)),
            SegmentOption("sidebar", stringResource(R.string.extras_layout_sidebar), stringResource(R.string.extras_layout_sidebar_cd))),
        if (state.extras.sidebar) "sidebar" else "row", { onIntent(HomeExtrasIntent.Sidebar(it.key == "sidebar")) },
    )
    if (state.extras.sidebar) {
        Overline(stringResource(R.string.extras_side), Modifier.padding(top = 20.dp, bottom = 8.dp))
        SegmentedChoice(
            listOf(SegmentOption("left", stringResource(R.string.extras_side_left), stringResource(R.string.extras_side_left)),
                SegmentOption("right", stringResource(R.string.extras_side_right), stringResource(R.string.extras_side_right))),
            if (state.extras.sideRight) "right" else "left", { onIntent(HomeExtrasIntent.SideRight(it.key == "right")) },
        )
        ListGroup(Modifier.padding(top = 12.dp)) {
            SwitchRow(stringResource(R.string.extras_clock_opposite), state.extras.clockOpposite, { onIntent(HomeExtrasIntent.ClockOpposite(it)) },
                subtitle = stringResource(R.string.extras_clock_opposite_sub))
        }
    }
    Overline(stringResource(R.string.extras_names), Modifier.padding(top = 20.dp, bottom = 8.dp))
    SegmentedChoice(
        listOf(SegmentOption(NameSize.LARGE.id, stringResource(R.string.extras_names_large), stringResource(R.string.extras_names_large)),
            SegmentOption(NameSize.SMALL.id, stringResource(R.string.extras_names_small), stringResource(R.string.extras_names_small)),
            SegmentOption(NameSize.HIDDEN.id, stringResource(R.string.extras_names_hidden), stringResource(R.string.extras_names_hidden))),
        state.extras.names.id, { o -> onIntent(HomeExtrasIntent.Names(NameSize.entries.first { it.id == o.key })) },
    )
    Body(stringResource(R.string.extras_names_note), Modifier.padding(top = 8.dp))
    Overline(stringResource(R.string.extras_widgets), Modifier.padding(top = 20.dp, bottom = 8.dp))
    ListGroup {
        SwitchRow(stringResource(R.string.extras_dock_only), state.extras.dockOnly, { onIntent(HomeExtrasIntent.DockOnly(it)) },
            subtitle = stringResource(R.string.extras_dock_only_sub))
        ListDivider()
        SwitchRow(stringResource(R.string.extras_continue), state.extras.continueCard, { onIntent(HomeExtrasIntent.Continue(it)) },
            subtitle = stringResource(R.string.extras_continue_sub))
        ListDivider()
        SwitchRow(stringResource(R.string.extras_no_ads), true, {}, subtitle = stringResource(R.string.extras_no_ads_sub), enabled = false)
    }
    if (state.changed) PrimaryButton(stringResource(R.string.extras_save), { onIntent(HomeExtrasIntent.Save) }, Modifier.fillMaxWidth().padding(top = 16.dp),
        guardObscured = true)
    Body(stringResource(R.string.extras_on_tv), Modifier.padding(top = 18.dp))
    if (!inPage) {
        Body(stringResource(R.string.extras_more), Modifier.padding(top = 10.dp))
        SecondaryButton(stringResource(R.string.extras_open_look), { onIntent(HomeExtrasIntent.OpenLook) }, Modifier.fillMaxWidth().padding(top = 10.dp))
    }
}

// A small TV with the home screen as the choices draw it: a mountain-dusk picture, the clock, the apps in a row
// or down a frosted sidebar, and the Continue card.
@Composable
internal fun HomePreview(e: HomeExtras, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val font = if (e.signal) Dots else FontFamily.Default
    val desc = stringResource(R.string.extras_preview_cd)
    val picture = themeThumb(e.theme).takeIf { e.themePicture }
    Box(
        modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF3B6E9E), Color(0xFF7FA6C4), Color(0xFFB0563C), Color(0xFF2A1E2A))))
            .border(1.dp, CytTheme.colors.rim, shape).semantics { contentDescription = desc },
    ) {
        if (picture != null) Image(painterResource(picture), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        val clockLeft = e.sidebar && (e.sideRight || !e.clockOpposite)
        Text("11:45", Modifier.align(if (clockLeft) Alignment.TopStart else Alignment.TopEnd).padding(top = 10.dp, start = 14.dp, end = 14.dp), color = Color.White,
            fontFamily = font, fontSize = if (e.signal) 26.sp else 28.sp, fontWeight = FontWeight.Light)
        Text(if (e.signal) "SAT 31.10" else "Saturday, 31 October", Modifier.align(if (clockLeft) Alignment.TopStart else Alignment.TopEnd).padding(top = 44.dp, start = 14.dp, end = 14.dp),
            color = Color.White.copy(alpha = 0.8f), fontFamily = font, fontSize = 8.sp)
        val apps = listOf(Color(0xFFE50914), Color(0xFF113CCF), Color(0xFFFF0000), Color(0xFF111111), Color(0xFF00A8E1), Color(0xFFFA2D48))
        // Below the row, the rest of the apps unless only the dock shows.
        if (!e.sidebar && !e.dockOnly) Row(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 0.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(9) { Box(Modifier.size(28.dp, 8.dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Color.White.copy(alpha = 0.35f))) }
        }
        val names = listOf("Netflix", "Disney+", "YouTube", "Apple TV", "Prime Video", "Music")
        if (e.sidebar) {
            Column(
                Modifier.align(if (e.sideRight) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 12.dp)
                    .width(when (e.names) { NameSize.HIDDEN -> 30.dp; NameSize.SMALL -> 90.dp; NameSize.LARGE -> 110.dp }).fillMaxHeight(0.82f)
                    .clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.18f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp)).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                apps.forEachIndexed { i, c ->
                    val focused = i == 2
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (focused) Color.White.copy(alpha = 0.95f) else Color.Transparent)
                        .padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c))
                        if (e.names != NameSize.HIDDEN) Text(if (e.signal) names[i].uppercase() else names[i], Modifier.padding(start = 5.dp),
                            color = if (focused) Color(0xFF111111) else Color.White, fontFamily = font, fontSize = if (e.names == NameSize.SMALL) 6.sp else 7.sp, maxLines = 1)
                    }
                }
            }
        } else {
            if (e.names != NameSize.HIDDEN) Text(if (e.signal) "YOUTUBE" else "YouTube", Modifier.align(Alignment.BottomStart).padding(start = 66.dp, bottom = 52.dp),
                color = Color.White, fontFamily = font, fontSize = if (e.names == NameSize.SMALL) 9.sp else 14.sp)
            if (e.signal && e.names != NameSize.HIDDEN) Box(Modifier.align(Alignment.BottomStart).padding(start = 66.dp, bottom = 40.dp).width(1.dp).size(1.dp, 12.dp).background(Color.White))
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 12.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.18f)).border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp)).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                apps.forEachIndexed { i, c ->
                    Box(Modifier.size(if (i == 2) 22.dp else 18.dp).clip(RoundedCornerShape(5.dp)).background(c)
                        .then(if (i == 2) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(5.dp)) else Modifier))
                }
            }
        }
        if (e.continueCard) {
            Row(
                Modifier.align(if (e.sidebar && e.sideRight) Alignment.BottomStart else Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.2f)).border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFA2D48)))
                Column(Modifier.padding(start = 5.dp)) {
                    Text(if (e.signal) "CONTINUE IN MUSIC" else "Continue in Music", color = Color.White, fontFamily = font, fontSize = 7.sp, maxLines = 1)
                    Text(if (e.signal) "5 MIN AGO" else "Last opened · 5 min ago", color = Color.White.copy(alpha = 0.7f), fontFamily = font, fontSize = 6.sp, maxLines = 1)
                }
            }
        }
    }
}

internal fun themeThumb(t: HomeTheme): Int? = when (t) {
    HomeTheme.NONE -> null
    HomeTheme.GLASS -> R.drawable.uytv_theme_glass
    HomeTheme.SIGNAL -> R.drawable.uytv_theme_signal
    HomeTheme.SIGNAL_DARK -> R.drawable.uytv_theme_signal_dark
    HomeTheme.GAMING -> R.drawable.uytv_theme_gaming
    HomeTheme.ISLAMIC -> R.drawable.uytv_theme_islamic
    HomeTheme.HALLOWEEN -> R.drawable.uytv_theme_halloween
    HomeTheme.HORROR -> R.drawable.uytv_theme_horror
    HomeTheme.ANIME -> R.drawable.uytv_theme_anime
}

@Composable
private fun themeName(t: HomeTheme): String = stringResource(when (t) {
    HomeTheme.NONE -> R.string.extras_theme_none
    HomeTheme.GLASS -> R.string.extras_theme_glass
    HomeTheme.SIGNAL -> R.string.extras_theme_signal
    HomeTheme.SIGNAL_DARK -> R.string.extras_theme_signal_dark
    HomeTheme.GAMING -> R.string.extras_theme_gaming
    HomeTheme.ISLAMIC -> R.string.extras_theme_islamic
    HomeTheme.HALLOWEEN -> R.string.extras_theme_halloween
    HomeTheme.HORROR -> R.string.extras_theme_horror
    HomeTheme.ANIME -> R.string.extras_theme_anime
})

// The themes in a row of cards, each its picture and name; the chosen one has a ring.
@Composable
private fun ThemePicker(current: HomeTheme, onPick: (HomeTheme) -> Unit) {
    val c = CytTheme.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeTheme.entries.forEach { t ->
            val on = t == current
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier.width(132.dp).clip(shape).clickable(role = Role.RadioButton) { onPick(t) }
                    .semantics { this.selected = on }.padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).background(c.surface2)
                    .border(if (on) 2.dp else 1.dp, if (on) c.primary else c.rim, shape)) {
                    themeThumb(t)?.let { Image(painterResource(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Text(themeName(t), Modifier.padding(top = 6.dp), color = if (on) c.textPrimary else c.textSecondary,
                    style = CytTheme.type.bodySmall, maxLines = 1)
            }
        }
    }
}
