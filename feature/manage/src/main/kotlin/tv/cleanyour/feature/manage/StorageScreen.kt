// CytBottomSheet takes the Material 3 sheet state, which is still marked experimental.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package tv.cleanyour.feature.manage

import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.cleanyour.core.flows.ApkOffer
import tv.cleanyour.core.flows.StorageIntent
import tv.cleanyour.core.flows.StorageNote
import tv.cleanyour.core.flows.StorageState
import tv.cleanyour.core.probe.StorageVolume
import tv.cleanyour.ui.design.component.Banner
import tv.cleanyour.ui.design.component.BannerTone
import tv.cleanyour.ui.design.component.ConfirmQuestion
import tv.cleanyour.ui.design.component.CytBottomSheet
import tv.cleanyour.ui.design.component.CytIconButton
import tv.cleanyour.ui.design.component.CytTopBar
import tv.cleanyour.ui.design.component.Gap
import tv.cleanyour.ui.design.component.ListDivider
import tv.cleanyour.ui.design.component.ListGroup
import tv.cleanyour.ui.design.component.ListRow
import tv.cleanyour.ui.design.component.PrimaryButton
import tv.cleanyour.ui.design.component.ProgressBar
import tv.cleanyour.ui.design.component.ScreenScaffold
import tv.cleanyour.ui.design.component.ScreenTitle
import tv.cleanyour.ui.design.component.SecondaryButton
import tv.cleanyour.ui.design.component.Spinner
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.Glow

// Storage, files and apps: the TV's free space, files from this phone to its Download folder, and APK
// installs (StorageMachine). The app opens the system's file picker; `onPickFiles` and `onPickApk` do that.
@Composable
fun StorageScreen(
    state: StorageState,
    onIntent: (StorageIntent) -> Unit,
    onBack: () -> Unit,
    onPickFiles: () -> Unit,
    onPickApk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    fun bytes(n: Long): String = Formatter.formatShortFileSize(context, n)
    ScreenScaffold(Glow.Tv, modifier, topBar = {
        CytTopBar(onBack = onBack, trailing = {
            if (state.refreshing && !state.loading) Box(Modifier.padding(horizontal = 16.dp)) { Spinner() }
            else CytIconButton(CytIcons.Refresh, stringResource(R.string.storage_refresh_cd), { onIntent(StorageIntent.Refresh) })
        })
    }) {
        ScreenTitle(stringResource(R.string.storage_title), Modifier.padding(top = 20.dp))
        if (state.loading) {
            LiveLine(stringResource(R.string.storage_reading))
            return@ScreenScaffold
        }
        state.note?.let { NoteBanner(it, onIntent) }
        if (state.failed) {
            Banner(stringResource(R.string.storage_failed), BannerTone.WARNING, Modifier.padding(top = 16.dp),
                actionLabel = stringResource(R.string.storage_try_again), onAction = { onIntent(StorageIntent.Refresh) })
        }

        // The volumes: internal first, then every USB drive or SD card.
        state.storage?.volumes?.forEach { v -> VolumeCard(v, ::bytes) }

        // Send files.
        SectionLabel(stringResource(R.string.storage_send_title))
        Body(stringResource(R.string.storage_send_body))
        val t = state.transfer
        if (t != null) {
            LiveLine(msg(R.string.storage_sending, "done" to t.done, "of" to t.of, "name" to t.name))
            ProgressBar(if (t.size > 0) t.sent.toFloat() / t.size else 0f, Modifier.fillMaxWidth().padding(top = 10.dp))
            Body(msg(R.string.storage_sending_progress, "sent" to bytes(t.sent), "size" to bytes(t.size)), Modifier.padding(top = 6.dp))
        } else {
            PrimaryButton(stringResource(R.string.storage_send_button), onPickFiles, Modifier.padding(top = 12.dp), enabled = !state.busy)
        }

        // Install an app.
        SectionLabel(stringResource(R.string.storage_install_title))
        Body(stringResource(R.string.storage_install_body))
        when {
            state.reading -> LiveLine(stringResource(R.string.storage_reading_apk))
            state.installing != null -> LiveLine(msg(R.string.storage_installing, "name" to (state.installingName ?: "")))
            else -> SecondaryButton(stringResource(R.string.storage_install_button), onPickApk, Modifier.padding(top = 12.dp), enabled = !state.busy)
        }

        // What is in the Download folder now.
        SectionLabel(stringResource(R.string.storage_downloads_title))
        val files = state.storage?.downloads.orEmpty()
        if (files.isEmpty()) Body(stringResource(R.string.storage_downloads_empty))
        else ListGroup {
            files.forEachIndexed { i, f ->
                if (i > 0) ListDivider()
                ListRow(f.name, subtitle = msg(R.string.storage_file_line, "size" to bytes(f.size), "date" to shortDateTime(f.modifiedSeconds * 1000, locale)))
            }
        }
        Gap(24.dp)
    }
    state.offer?.let { o -> CytBottomSheet({ onIntent(StorageIntent.CancelInstall) }) { OfferContent(o, onIntent, ::bytes) } }
}

@Composable
private fun VolumeCard(v: StorageVolume, bytes: (Long) -> String) {
    val total = v.totalKb * 1024
    val used = v.usedKb * 1024
    val free = v.freeKb * 1024
    val percent = if (v.totalKb > 0) ((v.usedKb * 100) / v.totalKb).toInt() else 0
    ListGroup(Modifier.padding(top = 16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                if (v.kind == StorageVolume.Kind.INTERNAL) stringResource(R.string.storage_internal) else msg(R.string.storage_external, "id" to v.id),
                color = CytTheme.colors.textPrimary, style = CytTheme.type.bodyLarge,
            )
            Text(msg(R.string.storage_free_of, "free" to bytes(free), "total" to bytes(total)),
                Modifier.padding(top = 4.dp), color = CytTheme.colors.textSecondary, style = CytTheme.type.bodyMedium)
            ProgressBar(percent / 100f, Modifier.fillMaxWidth().padding(top = 12.dp))
            Text(msg(R.string.storage_used, "used" to bytes(used), "percent" to percent),
                Modifier.padding(top = 8.dp), color = if (percent >= 90) CytTheme.colors.danger else CytTheme.colors.textTertiary, style = CytTheme.type.bodySmall)
        }
    }
}

@Composable
private fun NoteBanner(n: StorageNote, onIntent: (StorageIntent) -> Unit) {
    val (text, tone) = when (n) {
        is StorageNote.Sent -> msg(R.string.storage_note_sent, "count" to n.count, "names" to n.names.joinToString(", ")) to BannerTone.SUCCESS
        is StorageNote.SendFailed -> msg(R.string.storage_note_send_failed, "name" to n.name, "detail" to n.detail) to BannerTone.DANGER
        is StorageNote.Installed -> msg(R.string.storage_note_installed, "name" to n.name) to BannerTone.SUCCESS
        is StorageNote.InstallFailed -> msg(R.string.storage_note_install_failed, "name" to n.name, "code" to n.code) to BannerTone.DANGER
        StorageNote.NotAnApk -> stringResource(R.string.storage_note_not_apk) to BannerTone.WARNING
        StorageNote.TooBig -> stringResource(R.string.storage_note_too_big) to BannerTone.WARNING
        StorageNote.Unreadable -> stringResource(R.string.storage_note_unreadable) to BannerTone.WARNING
        StorageNote.NoTv -> stringResource(R.string.storage_note_no_tv) to BannerTone.WARNING
    }
    Banner(text, tone, Modifier.padding(top = 16.dp), actionLabel = stringResource(R.string.storage_note_ok), onAction = { onIntent(StorageIntent.DismissNote) })
}

@Composable
internal fun ColumnScope.OfferContent(o: ApkOffer, onIntent: (StorageIntent) -> Unit, bytes: (Long) -> String) {
    val what = msg(R.string.storage_offer_body, "pkg" to o.info.pkg.value, "version" to "${o.info.versionName} (${o.info.versionCode})", "size" to bytes(o.blob.size))
    val installed = o.installedVersion
    val state = when {
        installed == null -> stringResource(R.string.storage_offer_new)
        o.isDowngrade -> msg(R.string.storage_offer_downgrade, "from" to (installed ?: 0L))
        o.isSame -> stringResource(R.string.storage_offer_same)
        else -> msg(R.string.storage_offer_update, "from" to (installed ?: 0L), "to" to o.info.versionCode)
    }
    ConfirmQuestion(
        msg(R.string.storage_offer_title, "name" to o.name), "$what $state",
        stringResource(R.string.storage_offer_install), { onIntent(StorageIntent.ConfirmInstall) },
        stringResource(R.string.storage_offer_cancel), { onIntent(StorageIntent.CancelInstall) },
    )
}
