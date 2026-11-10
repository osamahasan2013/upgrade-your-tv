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
