package tv.cleanyour.core.probe

import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.session.TvSession

// Upgrade Your TV (Storage and files): the TV's storage and what is in its Download folder. Read-only
// constant lines (ProbeScripts.STORAGE, ProbeScripts.DOWNLOADS), plus the media scan after a file is
// sent, so the TV's players and galleries see it. Not part of the frozen Prober, like LaunchableApps.
data class StorageVolume(val kind: Kind, val id: String, val mount: String, val totalKb: Long, val usedKb: Long, val freeKb: Long) {
    enum class Kind { INTERNAL, EXTERNAL }
}

data class TvFile(val name: String, val size: Long, val modifiedSeconds: Long)

data class TvStorage(val volumes: List<StorageVolume>, val downloads: List<TvFile>) {
    val internal: StorageVolume? get() = volumes.firstOrNull { it.kind == StorageVolume.Kind.INTERNAL }
}

interface StorageRead {
    suspend fun storage(session: TvSession): TvStorage
    suspend fun mediaScan(session: TvSession, file: RemotePath)
    // One file of the Download folder removed; true when the TV said it is gone.
    suspend fun removeDownload(session: TvSession, name: String): Boolean
}

// Upgrade Your TV (Video background): wallpaper.mp4 in the launcher's theme folder. `send` pushes the
// file from the phone (streamed) and puts it in place; `remove` takes it out; both restart Home after.
interface VideoBackground {
    suspend fun videoPresent(session: TvSession): Boolean
    // `asApp`: the launcher is on the TV as an app, not as its Home, so it is opened by name afterwards.
    suspend fun sendVideo(session: TvSession, sdk: Int, open: () -> java.io.InputStream, size: Long, asApp: Boolean, onProgress: (Long) -> Unit)
    suspend fun removeVideo(session: TvSession, asApp: Boolean)
    suspend fun openHome(session: TvSession)
}

// The TV's screen, again and again (Manage's card while it is on screen). `frame` leaves its temp file on
// the TV between frames, so a frame is two round trips; `endLive` removes it.
// The remote in the app (1.7.0): a button press as the TV's own remote sends it, and typed text, through
// `input`. Only key codes from RemoteKeyCodes and text from the safe set ever reach the shell.
// 1.9.8: the launcher allowed to put its Home button helper back after the TV was off. True when both lines ran.
interface HelperKeep {
    suspend fun keepHelper(session: TvSession): Boolean
}

interface RemoteInput {
    suspend fun key(session: TvSession, code: Int, long: Boolean = false)
    suspend fun text(session: TvSession, text: String)
    // 1.9.4: the key injector on the TV (the launcher's UytvKeys) started ahead of the first press, and ended.
    suspend fun warmKeys(session: TvSession) {}
    suspend fun endKeys(session: TvSession) {}
}

// "Speed up the TV" (1.9.1): Android's animation speed (the three animation scales), free memory (background
// processes ended, as Android does when it runs short) and app caches trimmed. Fixed lines only.
interface SpeedUp {
    suspend fun animationScale(session: TvSession): Float?
    suspend fun setAnimationScale(session: TvSession, scale: Float)
    suspend fun freeMemory(session: TvSession)
    suspend fun trimCaches(session: TvSession)
}

interface LiveScreen {
    suspend fun frame(session: TvSession): ByteArray?
    suspend fun endLive(session: TvSession)
}

// `df -k` as toybox prints it (Android 8 to 14): "Filesystem 1K-blocks Used Available Use% Mounted on",
// one mount a line. Internal storage is /data (the shared storage under /storage/emulated is the same
// space). A USB drive or SD card is /storage/<id> or /mnt/media_rw/<id>; each id is listed once.
object StorageParser {
    private val EXTERNAL = Regex("^/(?:storage|mnt/media_rw)/([A-Za-z0-9-]{1,40})$")

    fun parseDf(lines: Sequence<String>): List<StorageVolume> {
        var internal: StorageVolume? = null
        val external = LinkedHashMap<String, StorageVolume>()
        for (raw in lines) {
            val t = raw.trim().split(Regex("\\s+"))
            if (t.size < 6) continue
            val total = t[1].toLongOrNull() ?: continue
            val used = t[2].toLongOrNull() ?: continue
            val free = t[3].toLongOrNull() ?: continue
            if (total <= 0) continue
            val mount = t.last()
            if (mount == "/data") { internal = StorageVolume(StorageVolume.Kind.INTERNAL, "data", mount, total, used, free); continue }
            val id = EXTERNAL.matchEntire(mount)?.groupValues?.get(1) ?: continue
            if (id == "emulated" || id == "self" || id.startsWith("emulated")) continue
            if (id !in external || mount.startsWith("/storage/")) external[id] = StorageVolume(StorageVolume.Kind.EXTERNAL, id, mount, total, used, free)
        }
        return listOfNotNull(internal) + external.values
    }

    // `stat -c '%F|%s|%Y|%n'` over the Download folder: regular files only, newest first.
    fun parseDownloads(lines: Sequence<String>, max: Int = 50): List<TvFile> = lines.mapNotNull { raw ->
        val p = raw.trim().split('|', limit = 4)
        if (p.size < 4 || p[0] != "regular file") return@mapNotNull null
        val size = p[1].toLongOrNull() ?: return@mapNotNull null
        val mtime = p[2].toLongOrNull() ?: 0L
        val name = p[3].substringAfterLast('/')
        if (name.isEmpty()) null else TvFile(name, size, mtime)
    }.sortedByDescending { it.modifiedSeconds }.take(max).toList()
}
