package tv.cleanyour.feature.manage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.util.TimeZone
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.cleanyour.core.engine.PauseReason
import tv.cleanyour.core.flows.AllAppsState
import tv.cleanyour.core.flows.ApkOffer
import tv.cleanyour.core.flows.FileTransfer
import tv.cleanyour.core.flows.StorageNote
import tv.cleanyour.core.flows.StorageState
import tv.cleanyour.core.flows.DevicesState
import tv.cleanyour.core.flows.KnownDevice
import tv.cleanyour.core.flows.NewDevice
import tv.cleanyour.core.flows.VideoBackgroundState
import tv.cleanyour.core.flows.VideoHome
import tv.cleanyour.core.flows.VideoNote
import tv.cleanyour.core.model.ApkInfo
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.probe.StorageVolume
import tv.cleanyour.core.probe.TvFile
import tv.cleanyour.core.probe.TvStorage
import tv.cleanyour.core.flows.AppConfirm
import tv.cleanyour.core.flows.AppFilter
import tv.cleanyour.core.flows.AppRow
import tv.cleanyour.core.flows.AppPrefsValue
import tv.cleanyour.core.flows.AppSettingsState
import tv.cleanyour.core.flows.ChipState
import tv.cleanyour.core.flows.LeftoversState
import tv.cleanyour.core.flows.ManageState
import tv.cleanyour.core.flows.Reach
import tv.cleanyour.core.flows.ReachCheck
import tv.cleanyour.core.flows.ReportKind
import tv.cleanyour.core.flows.Seen
import tv.cleanyour.core.flows.ReportState
import tv.cleanyour.core.flows.HomePath
import tv.cleanyour.core.flows.RestartTally
import tv.cleanyour.core.flows.SentReport
import tv.cleanyour.core.flows.OffGroup
import tv.cleanyour.core.flows.OffRow
import tv.cleanyour.core.flows.OpenWrite
import tv.cleanyour.core.flows.OpenWriteAction
import tv.cleanyour.core.flows.SwitchedOffState
import tv.cleanyour.core.flows.TvCard
import tv.cleanyour.core.flows.TvSettingsState
import tv.cleanyour.core.flows.UpdateCheck
import tv.cleanyour.core.model.DebugState
import tv.cleanyour.core.model.Level
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PlanId
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.planner.PlanKind
import tv.cleanyour.testing.fixtures.Samples

// Every section 4 state of :feature:manage (PRODUCT-SPEC 4: M1, M1a, the open-run card, M4, M5,
// M7, M8 and their sheets), each in dark, light, 200% font and right to left, with ATF on.

private fun fakeShot(): ByteArray {
    val bmp = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(0xFF05060A.toInt())
    val paint = Paint().apply { color = 0xFF2A2F45.toInt() }
    repeat(4) { i -> canvas.drawRoundRect(RectF(70f + i * 130f, 220f, 180f + i * 130f, 320f), 16f, 16f, paint) }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}

private const val NOW = Samples.NOW_MS
private const val FINGERPRINT = "3A:91:0C:7E:55:12:9B:44:E0:21:6D:88:F3:0A:BC:42"
private val tvId = Samples.miTvId()
private val dockNames = listOf("Netflix", "YouTube", "Prime Video", "Live TV")
private val shotPng by lazy { fakeShot() }

private fun card(
    clean: Boolean = true, level: Level = Level.SIMPLE, chip: ChipState = ChipState.CONNECTED, thumbnail: Boolean = true,
    switchedOff: Int = 23, room: String? = "Living room", restorePointAtMs: Long? = NOW, debugging: DebugState = DebugState.ON,
) = TvCard(tvId, "Mi TV 4A", room, clean, NOW, level, chip, if (thumbnail) shotPng else null, switchedOff, dockNames, restorePointAtMs, debugging)

private fun manage(card: TvCard = card(), offline: Boolean = false, reconnecting: Boolean = false, others: List<TvCard> = emptyList()) =
    ManageState(cards = listOf(card) + others, selected = tvId, offline = offline, reconnecting = reconnecting, loading = false)

private fun off(pkg: String, name: String, description: String?, off: Boolean = true) = OffRow(PackageName(pkg), name, description, off)

private val offGroups = listOf(
    OffGroup("ads", "Adverts and sponsored rows", listOf(
        off("com.mitv.patchwall", "PatchWall", "Xiaomi's home screen with adverts"),
        off("com.mitv.promos", "Mi TV+ promos", "Promoted content rows"),
        off("com.glance.tv", "Glance", "Adverts on the lock screen"),
    )),
    OffGroup("tracking", "Tracking and analytics", listOf(
        off("com.xiaomi.analytics", "Xiaomi Analytics", "Reports what you watch to Xiaomi"),
        off("com.alphonso.tv", "Alphonso", "Listens to what's on to sell ads"),
    )),
    OffGroup("other", "", listOf(off("com.example.legacy", "Legacy app", null))),
)
private val askGroups = listOf(
    OffGroup("voice", "Voice assistant", listOf(
        off("com.google.android.katniss", "Google Assistant", "Voice search and commands", off = false),
    )),
    OffGroup("updates", "Software updates", listOf(
        off("com.mitv.ota", "System updater", "Kept on so the TV keeps getting security fixes.", off = false),
        off("com.mitv.otaextra", "Update helper", "Checks for new TV software", off = true),
    )),
)

private fun switchedOff(level: Level = Level.SIMPLE, groups: List<OffGroup> = offGroups, ask: List<OffGroup> = emptyList(), busy: String? = null, count: Int = 6) =
    SwitchedOffState("Mi TV 4A", level, groups, ask, busy?.let { PackageName(it) }, loading = false, count = count)

private fun appSettings(update: UpdateCheck = UpdateCheck.Idle, backing: String = "STRONGBOX") = AppSettingsState(
    version = "0.1.0", prefs = AppPrefsValue(haptics = true, largeTextFollowsSystem = true),
    launcherPkg = "com.gathin.tvhome", launcherVersion = "0.1.0", launcherDigest = "9f2c41d07a6be8135c4d0e72ab19f5c3",
    keyBacking = backing, keyFingerprint = "AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:7F:2A", update = update,
)

private fun tvSettings(level: Level = Level.SIMPLE, room: String? = "Living room") = TvSettingsState("Mi TV 4A", room, level, "192.168.1.50", NOW)

private fun openWrite(kind: PlanKind = PlanKind.SWITCH_OFF, reason: PauseReason = PauseReason.CONNECTION_LOST, busy: Boolean = false, notTaken: Boolean = false,
                      nothingDone: Boolean = false, tookHome: String? = null) =
    OpenWrite(tvId, "Mi TV 4A", PlanId("01JAZ0000000000000000000"), kind, reason, busy, notTaken, nothingDone, tookHome)

// 0.3.2 B1 and B2: the report preview with the text the report builder makes (TvReportTest has the
// exact text; this is its shape, shortened). 0.3.3 D1: the TV report in its compact form, as the
// preview shows it and the form gets it.
private val tvReport = """
    Clean Your TV: TV report
    App: 0.3.2 (full)
    Phone: Samsung SM-S921B, Android 16

    TV
    Brand: Hisense
    Manufacturer: Hisense
    Model: 55A6H
    Device: HiTV-MT9602
    Android: 11 (SDK 30)
    Build: Hisense/HE55A6H/HiTV-MT9602:11/RTM6.230109.155/155:user/release-keys
    Google Play services: yes
    Home screen it came with: Google TV
    Read from the TV: 2026-10-23, before any change

    Matching
    Profile: brand set Hisense
    Home switch: blocked WIZARD_STAYS_ON

    Home screen
    Resolves to: com.google.android.apps.tv.launcherx/.home.HomeActivity
    Claimant: com.google.android.apps.tv.launcherx/.home.HomeActivity priority 2

    System apps (5)
    Switched off by this app (1):
    com.hisense.storedemo

    On, enabled (2):
    com.google.android.apps.tv.launcherx, com.google.android.tungsten.setupwraith

    On, default (2):
    android, com.android.tv.settings

    Other apps: 14 (not named)
""".trimIndent()

private val problemReport = """
    Clean Your TV: problem report
    App: 0.3.2 (play)
    Phone: Samsung SM-S921B, Android 16

    TV
    Brand: Insignia
    Model: NS-55F501NA26
    Device: mantis
    Android: 9 (SDK 28)
    Home screen it came with: Fire TV
    Profile: brand set Amazon
    Home switch: blocked TV_REFUSES
    Apps: 255 system, 14 other

    Diagnostics log (addresses, serial numbers, keys and codes removed)
    Clean Your TV 0.3.2, Android 36
    1792745068397 I setup: probe done in 5464 ms, 269 packages, profile brand set Amazon, tv Insignia NS-55F501NA26 (mantis) sdk 28
    1792745323148 I setup: run paused STEP_FAILED at step 72: undo step failed: Enable com.amazon.tv.launcher: nonzero_exit(1)
""".trimIndent()

// 0.4.8: the confirmation block (TvReport.confirmation), as the preview shows it.
private val confirmation = """
    Clean Your TV: confirmation
    Format: 1
    App: 0.4.8 (full)
    Build kind: test build
    Phone: Samsung SM-S921B, Android 16

    TV
    Brand: Xiaomi
    Manufacturer: Xiaomi
    Model: MiTV-AXSO0
    Device: dangal
    Android: 9 (SDK 28)
    Build: Xiaomi/dangal/dangal:9/PI/2120:user/release-keys

    Home screen
    Profile: exact xiaomi-mitv-axso0
    Home switch: planned

    Restarts
    Home path: list
    Held: 3
    Failed: 0
    Last restart: held
    First counted: 2026-10-28
    Last counted: 2026-10-30
    Counted by: 0.4.8 (full, test build)
    Look route: provider
    Checked: boot_id and Home only
""".trimIndent()

private fun held(n: Int, told: Long? = null) = RestartTally(bootId = "b", answered = true, path = HomePath.LIST, held = n, firstAtMs = NOW - 2 * 86_400_000, lastAtMs = NOW,
    build = "0.4.8 (full, test build)", toldAtMs = told, toldHeld = if (told != null) n else 0)

private fun report(kind: ReportKind, text: String = when (kind) { ReportKind.TV -> tvReport; ReportKind.CONFIRM -> confirmation; else -> problemReport }, inLink: Boolean = true) =
    ReportState(kind, loading = false, text = text, url = "https://github.com/osamahasan2013/upgrade-your-tv/issues/new", available = true, inLink = inLink)

private val tall = 1100.dp

private val playSettings = appSettings().copy(updateCheck = false, addOns = false)

// RF-STORES-030 M2: the Play build's Switched off at the Custom level. An updater an earlier full
// build switched off is listed with Put back; the ask groups have no updater pills.
private val playOffGroups = offGroups.dropLast(1) + OffGroup("updates", "Software updates", listOf(
    off("com.mitv.otaextra", "Update helper", "Checks for new TV software"),
)) + offGroups.last()
private val betterGroups = listOf(
    OffGroup(OffGroup.BETTER_BACK_ON, OffGroup.BETTER_BACK_ON_TITLE, listOf(
        off("com.tcl.xian.StartandroidService", "TCL start service", null).copy(reason = "TCL's software update needs this to find your TV."),
        off("com.google.android.tv.remote.service", "Remote service", "Lets a phone work as the remote").copy(reason = "Your remote's mic button needs this."),
    )),
)
private val playAskGroups = askGroups.filter { it.id != "updates" }

// All apps: a TCL Google TV's mix of system and installed apps, running and off.
private fun app(pkg: String, name: String, system: Boolean = true, off: Boolean = false, running: Boolean = false, locked: String? = null, warning: String? = null) =
    AppRow(PackageName(pkg), name, system, off, running, locked, warning, canUninstall = !system && locked == null)
private val allAppRows = listOf(
    app("com.android.tv.settings", "Settings", running = true, locked = "it is the TV's Settings app, the way to switch debugging back on"),
    app("com.google.android.apps.tv.launcherx", "Google TV Home", running = true, locked = "it is the home screen the TV uses now; without it the TV would show no home screen"),
    app("com.google.android.gms", "Google Play services", running = true, warning = "it is Google Play services"),
    app("com.android.vending", "Google Play Store", warning = "it is the Play Store"),
    app("com.google.android.youtube.tv", "YouTube", running = true),
    app("com.netflix.ninja", "Netflix", running = true),
    app("com.tcl.tvweishi", "TCL Channel", off = true),
    app("com.tcl.gamecenter", "TCL Game Center", off = true),
    app("com.google.android.tvrecommendations", "TV recommendations", running = true),
    app("com.tcl.suspension", "Inputs panel", running = true, warning = "it provides a TV input (HDMI, tuner or CEC)"),
    app("com.google.android.tungsten.setupwraith", "Setup", warning = "the first-run setup wizard holds on to the Home button on this TV"),
    app("com.amazon.amazonvideo.livingroom", "Prime Video", system = false),
    app("org.videolan.vlc", "VLC", system = false, running = true),
    app("com.wolf.launcher", "Wolf Launcher", system = false, warning = "it is a home screen app you installed, and we never switch those off"),
)
// Storage, files and apps: a TV with 16 GB inside and a 32 GB USB drive.
private val tvStorage = TvStorage(
    listOf(
        StorageVolume(StorageVolume.Kind.INTERNAL, "data", "/data", 10_913_648, 8_718_232, 2_195_416),
        StorageVolume(StorageVolume.Kind.EXTERNAL, "1A2B-3C4D", "/storage/1A2B-3C4D", 30_553_088, 9_048_576, 21_504_512),
    ),
    listOf(TvFile("Holiday_2026.mp4", 734_003_200, NOW / 1000), TvFile("menu.pdf", 2_310_144, NOW / 1000 - 86_400)),
)
private val vlcApk = ApkInfo(PackageName("org.videolan.vlc"), 13_050_408, "3.6.5", Sha256("b".repeat(64)), Sha256("a".repeat(64)), 41_943_040, "pm")
private fun storageShot(transfer: FileTransfer? = null, offer: ApkOffer? = null, note: StorageNote? = null, installing: Long? = null) =
    StorageState("Living room TV", tvStorage, loading = false, transfer = transfer, offer = offer, note = note, installing = installing,
        installingName = installing?.let { "VLC" })

private fun allApps(filter: AppFilter = AppFilter.ALL, showSystem: Boolean = true, query: String = "", busy: String? = null) =
    AllAppsState("Living room TV", allAppRows, filter, showSystem, query, busy?.let(::PackageName), loading = false,
        runningKnown = true, readAtMs = NOW)

object ManageShots {
    val all: List<Shot> = listOf(
        // M1 Manage
        Shot("manage_clean") { ManageScreen(manage(), {}) },
        Shot("manage_custom_level") { ManageScreen(manage(card(level = Level.CUSTOM)), {}) },
        Shot("manage_original") { ManageScreen(manage(card(clean = false, switchedOff = 0)), {}) },
        Shot("manage_offline") { ManageScreen(manage(card(chip = ChipState.OFFLINE), offline = true), {}) },
        Shot("manage_reconnecting") { ManageScreen(manage(card(chip = ChipState.OFFLINE), offline = true, reconnecting = true), {}) },
        Shot("manage_debugging_off") { ManageScreen(manage(card(chip = ChipState.DEBUGGING_OFF, debugging = DebugState.OFF)), {}) },
        Shot("manage_working") { ManageScreen(manage(card(chip = ChipState.WORKING)), {}) },
        Shot("manage_no_thumbnail") { ManageScreen(manage(card(thumbnail = false, room = null)), {}) },
        // 0.3.2 B1: a TV no profile knows invites the person to help add it.
        Shot("manage_help_add_tv", tall) { ManageScreen(manage(card().copy(helpAddTv = true)), {}) },
        // 0.3.3 D2 (issue #3): the new home screen isn't on this TV; the Home screen row says so.
        Shot("manage_own_home", tall) { ManageScreen(manage(card().copy(helpAddTv = true, ownHome = true, dockNames = emptyList())), {}) },
        // 0.4.7 (UX finding 8): the new home screen installed but not in use; (must 4) the report sent before.
        Shot("manage_home_not_in_use", tall) { ManageScreen(manage(card().copy(homeInstalledNotInUse = true, dockNames = emptyList())), {}) },
        Shot("manage_report_sent", tall) { ManageScreen(manage(card().copy(helpAddTv = true, ownHome = true, dockNames = emptyList(), reportSentAtMs = NOW - 86_400_000)), {}) },
        // WP4 (C6): one line for the apps the list for this TV now keeps on.
        Shot("manage_better_back_on", tall) { ManageScreen(manage(card().copy(betterBackOn = 2)), {}) },
        // 0.4.6: a setup wizard claims Home under the new home screen; the way back, and while it runs.
        Shot("manage_wizard_home", tall) { ManageScreen(manage(card().copy(wizardHome = true)), {}) },
        Shot("manage_wizard_home_working", tall) { ManageScreen(manage(card().copy(wizardHome = true)).copy(switchingHome = true), {}) },
        // 0.4.7 (StockHomeBack): the TV turned its own home screen back on (XGIMI's remote buttons); Run Setup again leads.
        Shot("manage_stock_home_back", tall) { ManageScreen(manage(card().copy(stockHomeBack = true)), {}) },
        // RFC-HELPER-1 (test build): the Home button helper is on; the Home screen row says so.
        Shot("manage_home_helper", tall) { ManageScreen(manage(card().copy(homeHelperOn = true)), {}) },
        // 0.4.8 (Restarts): held through restarts, with "Tell us it worked"; told already; a restart that failed.
        Shot("manage_restarts_held", tall) { ManageScreen(manage(card().copy(restarts = held(3))), {}) },
        Shot("manage_restarts_told", tall) { ManageScreen(manage(card().copy(restarts = held(1, told = NOW))), {}) },
        Shot("manage_restart_failed", tall) { ManageScreen(manage(card().copy(restarts = held(2).copy(failed = 1, lastFailed = true, answered = false))), {}) },
        Shot("manage_two_tvs", tall) {
            val other = card().copy(id = TvId("1111222233334444"), name = "Bedroom Chromecast", room = "Bedroom")
            ManageScreen(manage(others = listOf(other)), {})
        },

        // 0.3.2 B1 and B2: the report preview
        Shot("report_tv", tall) { ReportScreen(report(ReportKind.TV), {}, {}) },
        Shot("report_problem", tall) { ReportScreen(report(ReportKind.PROBLEM), {}, {}) },
        // 0.3.3 D1: too long for the link, so the screen says it copies the report for the paste.
        Shot("report_tv_too_long", tall) { ReportScreen(report(ReportKind.TV, inLink = false), {}, {}) },
        // 0.4.7 (must 4): the report sent before, without and with the issue's link.
        Shot("report_tv_sent", tall) { ReportScreen(report(ReportKind.TV).copy(sent = SentReport(NOW - 86_400_000)), {}, {}) },
        Shot("report_tv_sent_issue", tall) { ReportScreen(report(ReportKind.TV).copy(sent = SentReport(NOW - 86_400_000, "https://github.com/osamahasan2013/upgrade-your-tv/issues/1368")), {}, {}) },
        // 0.4.8: "Tell us it worked", the confirmation's preview, and before a restart was counted.
        Shot("report_confirm", tall) { ReportScreen(report(ReportKind.CONFIRM), {}, {}) },
        Shot("report_confirm_unavailable") { ReportScreen(ReportState(ReportKind.CONFIRM, loading = false, available = false), {}, {}) },
        Shot("report_loading") { ReportScreen(ReportState(ReportKind.TV), {}, {}) },
        Shot("report_unavailable") { ReportScreen(ReportState(ReportKind.TV, loading = false, available = false), {}, {}) },

        // M1a the debugging step for a returning write
        Shot("debugstep_waiting") { SheetOver({ ManageScreen(manage(), {}) }) { DebugStepContent(Reach.NeedsDebugging(tvId, "Mi TV 4A"), ReachCheck(), {}) } },
        Shot("debugstep_connecting") { SheetOver({ ManageScreen(manage(), {}) }) { DebugStepContent(Reach.Connecting(tvId, "Mi TV 4A"), ReachCheck(), {}) } },
        // RFC-REACH-1: each case in its own words, the check under "Show details" and the hint after 30 s
        Shot("debugstep_switched_off") { SheetOver({ ManageScreen(manage(), {}) }) { DebugStepContent(Reach.NeedsDebugging(tvId, "Mi TV 4A"), ReachCheck(switchedOff = true), {}) } },
        Shot("debugstep_not_on_wifi") { SheetOver({ ManageScreen(manage(), {}) }) { DebugStepContent(Reach.WrongNetwork(tvId, "Mi TV 4A"), ReachCheck(onWifi = Seen.NO), {}) } },
        Shot("debugstep_allow") { SheetOver({ ManageScreen(manage(), {}) }) { DebugStepContent(Reach.AwaitingAllow(tvId, "Mi TV 4A", FINGERPRINT), ReachCheck(asksToAllow = Seen.YES), {}) } },
        Shot("debugstep_details", 1400.dp) { SheetOver({ ManageScreen(manage(), {}) }) {
            DebugStepContent(Reach.NeedsDebugging(tvId, "Mi TV 4A"), ReachCheck(Seen.YES, Seen.NO, Seen.NO, Seen.CHECKING, Seen.NO, switchedOff = true, waitedMs = 8_000), {}, initiallyOpen = true)
        } },
        Shot("debugstep_stuck", 1500.dp) { SheetOver({ ManageScreen(manage(), {}) }) {
            DebugStepContent(Reach.NeedsDebugging(tvId, "Mi TV 4A"), ReachCheck(Seen.YES, Seen.YES, Seen.NO, Seen.NO, Seen.NO, switchedOff = true, waitedMs = 34_000), {},
                details = DetailsUi(copied = true), initiallyOpen = true, onAddress = {})
        } },
        // 0.4.8: "Enter the TV's address" on the step (Setup's entry), and the row for the address typed there
        Shot("debugstep_address") { SheetOver({ ManageScreen(manage(), {}) }) { AddressStep({ false }, {}, initial = "8.8.8.8", initialError = true) } },
        Shot("debugstep_typed", 1500.dp) { SheetOver({ ManageScreen(manage(), {}) }) {
            DebugStepContent(Reach.NeedsDebugging(tvId, "Mi TV 4A"), ReachCheck(Seen.YES, Seen.NO, Seen.NO, Seen.NO, Seen.NO, waitedMs = 40_000, typed = Seen.NO), {},
                initiallyOpen = true, onAddress = {})
        } },
        Shot("debugstep_collecting", 1400.dp) { SheetOver({ ManageScreen(manage(), {}) }) {
            DebugStepContent(Reach.NeedsDebugging(tvId, "Mi TV 4A"), ReachCheck(Seen.YES, Seen.UNKNOWN, Seen.NO, Seen.NO, Seen.NO, waitedMs = 12_000), {},
                details = DetailsUi(busy = true), initiallyOpen = true)
        } },

        // The open-run card (R5-W8 O2)
        Shot("openwrite_connection_lost") { SheetOver({ ManageScreen(manage(), {}) }) { OpenWriteContent(openWrite(), {}, {}, {}) } },
        // Undo and Stop here ask first, in the same sheet.
        Shot("openwrite_confirm_undo") { SheetOver({ ManageScreen(manage(), {}) }) { OpenWriteContent(openWrite(), {}, {}, {}, initiallyAsking = OpenWriteAction.UNDO) } },
        Shot("openwrite_confirm_stop_here") { SheetOver({ ManageScreen(manage(), {}) }) { OpenWriteContent(openWrite(), {}, {}, {}, initiallyAsking = OpenWriteAction.STOP_HERE) } },
        Shot("openwrite_step_failed") { SheetOver({ ManageScreen(manage(), {}) }) { OpenWriteContent(openWrite(reason = PauseReason.STEP_FAILED), {}, {}, {}) } },
        Shot("openwrite_busy") { SheetOver({ ManageScreen(manage(), {}) }) { OpenWriteContent(openWrite(busy = true), {}, {}, {}) } },
        Shot("openwrite_not_taken") { SheetOver({ ManageScreen(manage(), {}) }) { OpenWriteContent(openWrite(notTaken = true), {}, {}, {}) } },
        // QA of 0.4.5: the TV changed after we read it (STALE): before any step, and partway through.
        Shot("openwrite_stale_nothing_done") { SheetOver({ ManageScreen(manage(), {}) }) { OpenWriteContent(openWrite(kind = PlanKind.THEME, reason = PauseReason.STALE, nothingDone = true), {}, {}, {}) } },
        Shot("openwrite_stale_partway") { SheetOver({ ManageScreen(manage(), {}) }) { OpenWriteContent(openWrite(kind = PlanKind.THEME, reason = PauseReason.STALE), {}, {}, {}) } },
        // 0.4.7 (UX finding 19): a look send that paused: "Your look didn't reach the TV", Send it again, Undo.
        Shot("openwrite_look_failed") { SheetOver({ ManageScreen(manage(), {}) }) { OpenWriteContent(openWrite(kind = PlanKind.THEME, reason = PauseReason.STEP_FAILED), {}, {}, {}) } },
        // 0.4.6: the app a put back switched on took the Home button.
        Shot("openwrite_took_home") { SheetOver({ ManageScreen(manage(), {}) }) { OpenWriteContent(openWrite(kind = PlanKind.PUT_BACK, reason = PauseReason.STEP_FAILED, tookHome = "Android TV Home"), {}, {}, {}) } },
        Shot("openwrite_setup") { SheetOver({ ManageScreen(manage(), {}) }) { OpenWriteContent(openWrite(kind = PlanKind.SETUP), {}, {}, {}) } },

        // M4 Switched off
        Shot("switchedoff_groups", tall) { SwitchedOffScreen(switchedOff(), {}, {}) },
        // All apps
        Shot("allapps_all", 2000.dp) { AllAppsScreen(allApps(), {}, {}) },
        Shot("allapps_running", 1500.dp) { AllAppsScreen(allApps(filter = AppFilter.RUNNING), {}, {}) },
        Shot("allapps_off", tall) { AllAppsScreen(allApps(filter = AppFilter.OFF), {}, {}) },
        Shot("allapps_installed_only", tall) { AllAppsScreen(allApps(showSystem = false), {}, {}) },
        Shot("allapps_busy", 2000.dp) { AllAppsScreen(allApps(busy = "com.google.android.youtube.tv"), {}, {}) },
        Shot("allapps_loading") { AllAppsScreen(AllAppsState(), {}, {}) },
        // Storage, files and apps
        Shot("storage_overview", tall) { StorageScreen(storageShot(), {}, {}, {}, {}) },
        Shot("storage_sending", tall) { StorageScreen(storageShot(transfer = FileTransfer("Holiday_2026.mp4", 312_000_000, 734_003_200, 1, 2)), {}, {}, {}, {}) },
        Shot("storage_installed", tall) { StorageScreen(storageShot(note = StorageNote.Installed("VLC")), {}, {}, {}, {}) },
        Shot("storage_offer") {
            SheetOver({ StorageScreen(storageShot(), {}, {}, {}, {}) }) {
                OfferContent(ApkOffer("content://x", "vlc.apk", vlcApk, BlobRef(Sha256("a".repeat(64)), 41_943_040, "/x"), "VLC", 13_050_100), {}, { "40 MB" })
            }
        },
        // Devices
        Shot("devices_list", tall) { DevicesScreen(DevicesState(
            known = listOf(
                KnownDevice(tvId, "TCL C645", "Living room", "55C645", "192.168.1.20", online = true, connected = true, connecting = false),
                KnownDevice(TvId("00000000000000b1"), "Xiaomi TV Box S", "Bedroom", "MDZ-28-AA", "192.168.1.31", online = true, connected = false, connecting = false),
                KnownDevice(TvId("00000000000000c2"), "TCL P635", "Office", "43P635", "192.168.1.44", online = false, connected = false, connecting = false),
            ),
            found = listOf(NewDevice("192.168.1.52", "Kitchen TV", "Mi TV 4A", true)),
            searching = false), {}, {}) },
        Shot("devices_searching", tall) { DevicesScreen(DevicesState(), {}, {}) },
        // Video background
        Shot("video_on") { VideoBackgroundScreen(VideoBackgroundState("Living room TV", loading = false, home = VideoHome.READY, present = true), {}, {}, {}) },
        Shot("video_off") { VideoBackgroundScreen(VideoBackgroundState("Living room TV", loading = false, home = VideoHome.READY, present = false), {}, {}, {}) },
        Shot("video_sending") { VideoBackgroundScreen(VideoBackgroundState("Living room TV", loading = false, home = VideoHome.READY, present = false,
            sending = 12_000_000L to 31_457_280L), {}, {}, {}) },
        Shot("video_sent") { VideoBackgroundScreen(VideoBackgroundState("Living room TV", loading = false, home = VideoHome.READY, present = true,
            note = VideoNote.Sent), {}, {}, {}) },
        Shot("video_other_key") { VideoBackgroundScreen(VideoBackgroundState("Living room TV", loading = false, home = VideoHome.OTHER_KEY), {}, {}, {}) },
        Shot("remote", 1700.dp) { RemoteScreen(tv.cleanyour.core.flows.RemoteState("Living room TV", connected = true, connecting = false, pressing = 23), {}, {}, {}) },
        Shot("remote_no_tv", 1700.dp) { RemoteScreen(tv.cleanyour.core.flows.RemoteState("Living room TV", connecting = false, note = tv.cleanyour.core.flows.RemoteNote.NoTv), {}, {}, {}) },
        Shot("buttons", 1700.dp) { RemoteButtonsScreen(buttonsState(), {}, {}) },
        Shot("buttons_needs_helper") { RemoteButtonsScreen(tv.cleanyour.core.flows.RemoteButtonsState("Living room TV", loading = false), {}, {}) },
        Shot("buttons_pick", 1500.dp) { SheetOver({ RemoteButtonsScreen(buttonsState(), {}, {}) }) {
            TargetPicker(tv.cleanyour.core.flows.RemoteButton.RED, false, buttonsState(), {})
        } },
        Shot("extras_signal_sidebar", 1600.dp) { HomeExtrasScreen(tv.cleanyour.core.flows.HomeExtrasState("Living room TV", loading = false,
            extras = tv.cleanyour.core.flows.HomeExtras(signal = true, sidebar = true, continueCard = true), launcherOn = true), {}, {}) },
        Shot("extras_signal_row", 1600.dp) { HomeExtrasScreen(tv.cleanyour.core.flows.HomeExtrasState("Living room TV", loading = false,
            extras = tv.cleanyour.core.flows.HomeExtras(signal = true, continueCard = true), launcherOn = true), {}, {}) },
        Shot("extras_small_right_dockonly", 1800.dp) { HomeExtrasScreen(tv.cleanyour.core.flows.HomeExtrasState("Living room TV", loading = false,
            extras = tv.cleanyour.core.flows.HomeExtras(sidebar = true, sideRight = true, names = tv.cleanyour.core.flows.NameSize.SMALL, dockOnly = true, continueCard = true), launcherOn = true), {}, {}) },
        Shot("extras_glass_sidebar", 1600.dp) { HomeExtrasScreen(tv.cleanyour.core.flows.HomeExtrasState("Living room TV", loading = false,
            extras = tv.cleanyour.core.flows.HomeExtras(sidebar = true), launcherOn = true), {}, {}) },
        Shot("video_offer_app") { VideoBackgroundScreen(VideoBackgroundState("Living room TV", loading = false, home = VideoHome.NOT_INSTALLED), {}, {}, {}) },
        Shot("video_as_app") { VideoBackgroundScreen(VideoBackgroundState("Living room TV", loading = false, home = VideoHome.AS_APP, present = true), {}, {}, {}) },
        Shot("allapps_added", 1500.dp) { AllAppsScreen(allApps(filter = AppFilter.INSTALLED), {}, {}) },
        Shot("storage_loading") { StorageScreen(StorageState(), {}, {}, {}, {}) },
        Shot("allapps_failed") { AllAppsScreen(AllAppsState(loading = false, failed = true), {}, {}) },
        Shot("allapps_uninstall") { SheetOver({ AllAppsScreen(allApps(), {}, {}) }) { ConfirmUninstallContent(allAppRows.first { it.pkg.value == "org.videolan.vlc" }, {}) } },
        Shot("storage_delete") { SheetOver({ StorageScreen(storageShot(), {}, {}, {}, {}) }) { DeleteContent("Holiday_2026.mp4", "734 MB", {}) } },
        Shot("allapps_confirm_off") { SheetOver({ AllAppsScreen(allApps(), {}, {}) }) { ConfirmOffContent(allAppRows[2], {}) } },
        Shot("switchedoff_busy", tall) { SwitchedOffScreen(switchedOff(busy = "com.glance.tv"), {}, {}) },
        Shot("switchedoff_custom", tall) { SwitchedOffScreen(switchedOff(Level.CUSTOM, ask = askGroups), {}, {}) },
        // WP4 (C6): "Better back on" at the top, each row with its reason and "Switch back on".
        Shot("switchedoff_better_back_on", tall) { SwitchedOffScreen(switchedOff(groups = betterGroups + offGroups, count = 8), {}, {}) },
        Shot("switchedoff_empty") { SwitchedOffScreen(switchedOff(groups = emptyList(), count = 0), {}, {}) },
        Shot("switchedoff_loading") { SwitchedOffScreen(SwitchedOffState(), {}, {}) },

        // M5 Check for leftovers
        Shot("leftovers_checking") { LeftoversScreen(LeftoversState.Checking, {}, {}) },
        Shot("leftovers_found") { LeftoversScreen(LeftoversState.Found(listOf("PatchWall", "Glance", "Xiaomi Analytics"), Samples.plan(emptyList())), {}, {}) },
        Shot("leftovers_found_one") { LeftoversScreen(LeftoversState.Found(listOf("PatchWall"), Samples.plan(emptyList())), {}, {}) },
        Shot("leftovers_nothing") { LeftoversScreen(LeftoversState.Nothing, {}, {}) },
        Shot("leftovers_working") { LeftoversScreen(LeftoversState.Working(40), {}, {}) },
        Shot("leftovers_done") { LeftoversScreen(LeftoversState.Done(3), {}, {}) },
        Shot("leftovers_failed") { LeftoversScreen(LeftoversState.Failed, {}, {}) },

        // M7 TV settings
        Shot("tvsettings_simple", tall) { TvSettingsScreen(tvSettings(), {}, {}) },
        Shot("tvsettings_custom", tall) { TvSettingsScreen(tvSettings(Level.CUSTOM, room = null), {}, {}) },
        Shot("tvsettings_forget") { SheetOver({ TvSettingsScreen(tvSettings(), {}, {}) }) { ForgetContent("Mi TV 4A") {} } },

        // M8 App settings
        Shot("appsettings_idle", tall) { AppSettingsScreen(appSettings(), {}, {}, {}) },
        Shot("appsettings_checking", tall) { AppSettingsScreen(appSettings(UpdateCheck.Checking), {}, {}, {}) },
        Shot("appsettings_update_available", tall) { AppSettingsScreen(appSettings(UpdateCheck.Available("0.2.0")), {}, {}, {}) },
        Shot("appsettings_up_to_date", tall) { AppSettingsScreen(appSettings(UpdateCheck.Latest), {}, {}, {}) },
        Shot("appsettings_update_failed", tall) { AppSettingsScreen(appSettings(UpdateCheck.Failed), {}, {}, {}) },
        Shot("appsettings_key_tee", tall) { AppSettingsScreen(appSettings(backing = "TEE"), {}, {}, {}) },
        Shot("appsettings_key_wrapped", tall) { AppSettingsScreen(appSettings(backing = "WRAPPED_SOFTWARE"), {}, {}, {}) },
        Shot("appsettings_licences") { SheetOver({ AppSettingsScreen(appSettings(), {}, {}, {}) }) { LicencesContent() } },
        Shot("appsettings_details") { SheetOver({ AppSettingsScreen(appSettings(), {}, {}, {}) }) { DetailsContent(DetailsUi(), {}) } },
        Shot("appsettings_details_copied") { SheetOver({ AppSettingsScreen(appSettings(), {}, {}, {}) }) { DetailsContent(DetailsUi(copied = true), {}) } },
        Shot("appsettings_privacy") { SheetOver({ AppSettingsScreen(appSettings(), {}, {}, {}) }) { PrivacyContent() } },
        Shot("appsettings_reset_key") { SheetOver({ AppSettingsScreen(appSettings(), {}, {}, {}) }) { ConfirmContent(AppConfirm.RESET_KEY, working = false) {} } },
        Shot("appsettings_delete_all") { SheetOver({ AppSettingsScreen(appSettings(), {}, {}, {}) }) { ConfirmContent(AppConfirm.DELETE_EVERYTHING, working = false) {} } },

        // The Google Play build (STORES-PLAN 1): no Cards and modules row (the add-ons entry), no
        // Check for updates, the privacy line without GitHub, and no updater pills in Switched off
        // (RF-STORES-030 M2). Every other screen is the same.
        Shot("play_manage_clean") { ManageScreen(manage().copy(addOns = false), {}) },
        Shot("play_appsettings_idle", tall) { AppSettingsScreen(playSettings, {}, {}, {}) },
        Shot("play_switchedoff_custom", tall) { SwitchedOffScreen(switchedOff(Level.CUSTOM, groups = playOffGroups, ask = playAskGroups, count = 7), {}, {}) },
        Shot("play_appsettings_privacy") { SheetOver({ AppSettingsScreen(playSettings, {}, {}, {}) }) { PrivacyContent(online = false) } },
    )
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h2400dp-xhdpi")
class ManageScreenshotTest(private val shot: Shot, private val variant: Variant) {
    @get:Rule val compose = createComposeRule()

    @Before fun fixedTimeZone() { TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }

    @Test fun shot() = compose.shoot(shot, variant)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun params(): List<Array<Any>> = ManageShots.all.flatMap { s -> Variant.entries.map { v -> arrayOf<Any>(s, v) } }
    }
}

private fun buttonsState(): tv.cleanyour.core.flows.RemoteButtonsState {
    val apps = listOf(
        tv.cleanyour.core.flows.TargetChoice("com.netflix.ninja", "Netflix"),
        tv.cleanyour.core.flows.TargetChoice("com.google.android.youtube.tv", "YouTube"),
        tv.cleanyour.core.flows.TargetChoice("com.amazon.amazonvideo.livingroom", "Prime Video"),
    )
    val map = listOf(
        tv.cleanyour.core.flows.ButtonMapping(tv.cleanyour.core.flows.RemoteButton.RED, false, "com.netflix.ninja"),
        tv.cleanyour.core.flows.ButtonMapping(tv.cleanyour.core.flows.RemoteButton.INPUT, false, "inputs"),
        tv.cleanyour.core.flows.ButtonMapping(tv.cleanyour.core.flows.RemoteButton.HOME_HOLD, true, "settings"),
    )
    return tv.cleanyour.core.flows.RemoteButtonsState("Living room TV", loading = false, helperOn = true, map = map, saved = emptyList(), apps = apps)
}

