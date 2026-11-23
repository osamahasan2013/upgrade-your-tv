package tv.cleanyour.core.flows

import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.cleanyour.core.model.ApkInfo
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.FILE_NAME_RE
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.probe.StorageRead
import tv.cleanyour.core.probe.TvFile
import tv.cleanyour.core.probe.TvStorage
import tv.cleanyour.core.session.InstallOutcome

// Upgrade Your TV: "Storage, files and apps". Three things, all over the same ADB session as the rest:
// - the TV's storage (internal and any USB drive or SD card) and the files in its Download folder;
// - sending files from the phone to the TV's Download folder (TvSession.pushStream, streamed from the
//   phone's file, checked against the TV's copy);
// - installing an APK from the phone (TvSession.installStream, the streamed install the add-ons use),
//   after reading the APK's package and version and asking for the phone's screen lock (GateKeeper).
// Sending and installing write to the TV, so both ask for the screen lock first.

// The phone's side: a file the person picked (a content URI as a string), read by the app.
data class PhoneFile(val name: String, val size: Long)
interface PhoneFiles {
    suspend fun describe(ref: String): PhoneFile?
    fun open(ref: String): InputStream?   // a fresh stream on each call
    companion object {
        val NONE: PhoneFiles = object : PhoneFiles {
            override suspend fun describe(ref: String): PhoneFile? = null
            override fun open(ref: String): InputStream? = null
        }
    }
}

data class FileTransfer(val name: String, val sent: Long, val size: Long, val done: Int, val of: Int)

data class ApkOffer(
    val ref: String,
    val fileName: String,
    val info: ApkInfo,
    val blob: BlobRef,
    val name: String,              // the app's name as this phone knows it, else the file name
    val installedVersion: Long?,   // the versionCode on the TV now, if it is installed
) {
    val isUpdate: Boolean get() = installedVersion != null && installedVersion < info.versionCode
    val isDowngrade: Boolean get() = installedVersion != null && installedVersion > info.versionCode
    val isSame: Boolean get() = installedVersion != null && installedVersion == info.versionCode
}

sealed interface StorageNote {
    data class Sent(val count: Int, val names: List<String>) : StorageNote
    data class SendFailed(val name: String, val detail: String) : StorageNote
    data class Installed(val name: String) : StorageNote
    data class Deleted(val name: String) : StorageNote
    data class DeleteFailed(val name: String) : StorageNote
    data class InstallFailed(val name: String, val code: String) : StorageNote
    data object NotAnApk : StorageNote
    data object TooBig : StorageNote
    data object Unreadable : StorageNote
    data object NoTv : StorageNote
}

data class StorageState(
    val tvName: String = "",
    val storage: TvStorage? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val failed: Boolean = false,
    val transfer: FileTransfer? = null,
    val reading: Boolean = false,          // copying and reading a picked APK on the phone
    val offer: ApkOffer? = null,           // the install question
    val installing: Long? = null,          // bytes sent of the APK being installed
    val installingName: String? = null,
    val note: StorageNote? = null,
    val delete: TvFile? = null,            // the file waiting for "Delete"
    val deleting: String? = null,          // the name being deleted now
) {
    val busy: Boolean get() = transfer != null || reading || installing != null || offer != null || delete != null || deleting != null
}

sealed interface StorageIntent {
    data object Refresh : StorageIntent
    data class FilesPicked(val refs: List<String>) : StorageIntent
    data class ApkPicked(val ref: String) : StorageIntent
    data object ConfirmInstall : StorageIntent
    data object CancelInstall : StorageIntent
    data object DismissNote : StorageIntent
    data class AskDelete(val name: String) : StorageIntent
    data object ConfirmDelete : StorageIntent
    data object CancelDelete : StorageIntent
}

class StorageMachine(
    private val d: ManageDeps,
    scope: CoroutineScope,
    private val tvId: TvId,
    private val files: PhoneFiles = d.phoneFiles,
) : Machine<StorageState, StorageIntent, ManageEffect>(StorageState(), scope) {

    private data class Loaded(val storage: TvStorage?, val tvName: String, val failed: Boolean)
    private data class Update(val f: (StorageState) -> StorageState)

    init { scope.launch { load() } }

    override fun reduce(state: StorageState, event: Any): StorageState = when (event) {
        is Loaded -> state.copy(storage = event.storage ?: state.storage, tvName = event.tvName, failed = event.failed && state.storage == null,
            loading = false, refreshing = false)
        is Update -> event.f(state)
        StorageIntent.DismissNote -> state.copy(note = null)
        is StorageIntent.AskDelete -> if (state.busy) state else state.copy(delete = state.storage?.downloads?.firstOrNull { it.name == event.name })
        StorageIntent.CancelDelete, StorageIntent.ConfirmDelete -> state.copy(delete = null)
        else -> state
    }

    override fun handle(event: Any, before: StorageState, after: StorageState) {
        when (event) {
            StorageIntent.Refresh -> if (!before.refreshing && !before.busy) scope.launch { load() }
            is StorageIntent.FilesPicked -> if (!before.busy && event.refs.isNotEmpty()) scope.launch { sendFiles(event.refs) }
            is StorageIntent.ApkPicked -> if (!before.busy) scope.launch { readApk(event.ref) }
            StorageIntent.ConfirmInstall -> before.offer?.let { o -> scope.launch { install(o) } }
            StorageIntent.ConfirmDelete -> before.delete?.let { f -> scope.launch { deleteFile(f) } }
            StorageIntent.CancelInstall -> before.offer?.let { o -> send(Update { it.copy(offer = null) }); scope.launch { attempt { d.blobs.delete(o.blob.sha256) } } }
            else -> {}
        }
    }

    private suspend fun session() = (d.access.session(tvId) as? AccessResult.Ok)?.session

    private suspend fun load() {
        val tv = d.tv(tvId) ?: return send(Loaded(null, "", failed = true))
        send(Update { it.copy(refreshing = true) })
        val s = session()
        val read = s?.let { ss -> (d.prober as? StorageRead)?.let { q -> attempt { q.storage(ss) }.getOrNull() } }
        send(Loaded(read, tv.nickname, failed = read == null))
    }

    // Files: one after another to the Download folder, under a name the TV's shell takes as it is.
    private suspend fun sendFiles(refs: List<String>) {
        val tv = d.tv(tvId) ?: return note(StorageNote.NoTv)
        val picked = refs.mapNotNull { r -> files.describe(r)?.let { r to it } }
        if (picked.isEmpty()) return note(StorageNote.Unreadable)
        d.gate.pass(tvId, tv.nickname) ?: return
        val session = session() ?: return note(StorageNote.NoTv)
        val sentNames = mutableListOf<String>()
        val taken = HashSet<String>()
        for ((i, p) in picked.withIndex()) {
            val (ref, f) = p
            val name = uniqueName(tvFileName(f.name, d.clock.nowMs()), taken)
            taken += name
            val dest = RemotePath(RemoteDir.DOWNLOAD, listOf(name))
            send(Update { it.copy(transfer = FileTransfer(name, 0, f.size, i + 1, picked.size)) })
            val r = attempt {
                session.pushStream({ files.open(ref) ?: error("the file can no longer be read") }, f.size, dest) { sent ->
                    send(Update { it.copy(transfer = it.transfer?.copy(sent = sent)) })
                }
            }
            if (r.isFailure) {
                send(Update { it.copy(transfer = null) })
                return note(StorageNote.SendFailed(f.name, r.exceptionOrNull()?.message ?: "failed"))
            }
            (d.prober as? StorageRead)?.let { q -> attempt { q.mediaScan(session, dest) } }
            sentNames += name
        }
        send(Update { it.copy(transfer = null) })
        note(StorageNote.Sent(sentNames.size, sentNames))
        load()
    }

    // An APK: copied into the phone's blob store (hashed on the way), read for its package and
    // version, then the question; nothing reaches the TV before Install.
    private suspend fun readApk(ref: String) {
        val f = files.describe(ref) ?: return note(StorageNote.Unreadable)
        if (f.size <= 0 || f.size > MAX_APK_BYTES) return note(StorageNote.TooBig)
        send(Update { it.copy(reading = true) })
        val blob = attempt {
            val sha = withContext(Dispatchers.IO) { (files.open(ref) ?: error("unreadable")).use { sha256(it) } }
            withContext(Dispatchers.IO) { (files.open(ref) ?: error("unreadable")).use { d.blobs.put(sha, it, f.size) } }
        }.getOrNull()
        if (blob == null) { send(Update { it.copy(reading = false) }); return note(StorageNote.Unreadable) }
        val info = attempt { d.inspector.inspect(blob) }.getOrNull()
        if (info == null) {
            attempt { d.blobs.delete(blob.sha256) }
            send(Update { it.copy(reading = false) }); return note(StorageNote.NotAnApk)
        }
        val latest = attempt { d.snapshots.latest(tvId) }.getOrNull()
        val st = latest?.packages?.get(info.pkg)
        val known = d.nameOf(info.pkg, st?.label)
        val name = if (known == info.pkg.value) f.name.substringBeforeLast('.').ifBlank { known } else known
        send(Update { it.copy(reading = false, offer = ApkOffer(ref, f.name, info, blob, name, st?.versionCode)) })
    }

    private suspend fun install(o: ApkOffer) {
        val tv = d.tv(tvId) ?: return note(StorageNote.NoTv)
        send(Update { it.copy(offer = null) })
        if (d.gate.pass(tvId, tv.nickname) == null) { attempt { d.blobs.delete(o.blob.sha256) }; return }
        val session = session() ?: run { attempt { d.blobs.delete(o.blob.sha256) }; return note(StorageNote.NoTv) }
        send(Update { it.copy(installing = 0L, installingName = o.name) })
        val r = attempt { session.installStream(o.blob, o.blob.size) { sent -> send(Update { it.copy(installing = sent) }) } }
        attempt { d.blobs.delete(o.blob.sha256) }
        send(Update { it.copy(installing = null, installingName = null) })
        when (val out = r.getOrNull()) {
            InstallOutcome.Installed -> {
                note(StorageNote.Installed(o.name))
                attempt { d.runner.freshSnapshot(session) }   // All apps and the dock see the new app
                load()
            }
            is InstallOutcome.Failed -> note(StorageNote.InstallFailed(o.name, out.code))
            null -> note(StorageNote.InstallFailed(o.name, r.exceptionOrNull()?.message ?: "failed"))
        }
    }

    // One file of the Download folder, after "Delete" and the screen lock.
    private suspend fun deleteFile(f: TvFile) {
        val tv = d.tv(tvId) ?: return note(StorageNote.NoTv)
        d.gate.pass(tvId, tv.nickname) ?: return
        val session = session() ?: return note(StorageNote.NoTv)
        send(Update { it.copy(deleting = f.name) })
        val ok = (d.prober as? StorageRead)?.let { q -> attempt { q.removeDownload(session, f.name) }.getOrDefault(false) } ?: false
        send(Update { it.copy(deleting = null) })
        note(if (ok) StorageNote.Deleted(f.name) else StorageNote.DeleteFailed(f.name))
        load()
    }

    private fun note(n: StorageNote) { send(Update { it.copy(note = n) }) }

    companion object {
        const val MAX_APK_BYTES = 1024L * 1024 * 1024   // 1 GiB; the session's install time grows with the size

        private fun sha256(input: InputStream): Sha256 {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n <= 0) break; md.update(buf, 0, n) }
            return Sha256(md.digest().joinToString("") { "%02x".format(it) })
        }

        // A name the TV's shell and RemotePath take (FILE_NAME_RE: ASCII letters, digits, . _ -, at
        // most 64): the phone's name with every other character as "_", its extension kept. A name with
        // nothing left (an Arabic one, say) becomes file-<date>-<time>.
        fun tvFileName(original: String, nowMs: Long): String {
            val base = original.substringAfterLast('/').trim()
            val ext = base.substringAfterLast('.', "").filter { it.code < 128 && it.isLetterOrDigit() }.take(10)
            val stem = if (base.contains('.')) base.substringBeforeLast('.') else base
            var s = stem.map { if (it.code < 128 && (it.isLetterOrDigit() || it == '.' || it == '_' || it == '-')) it else '_' }
                .joinToString("").replace(Regex("_+"), "_").trim('_', '.', '-')
            if (s.count { it.isLetterOrDigit() } < 2) s = "file-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date(nowMs))
            if (!s[0].isLetterOrDigit()) s = "f$s"
            val suffix = if (ext.isNotEmpty()) ".$ext" else ""
            s = s.take(64 - suffix.length).trimEnd('.', '-', '_')
            val name = s + suffix
            return if (FILE_NAME_RE.matches(name) && !name.contains("..")) name else "file-$nowMs$suffix".take(64)
        }

        // Two picks with the same name in one send get -2, -3... (a file already on the TV is replaced).
        fun uniqueName(name: String, taken: kotlin.collections.Set<String>): String {
            if (name !in taken) return name
            val stem = name.substringBeforeLast('.', name)
            val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
            var i = 2
            while (true) {
                val n = (stem.take(60 - ext.length) + "-" + i + ext)
                if (n !in taken) return n
                i++
            }
        }
    }
}
