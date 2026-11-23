package tv.cleanyour.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.cleanyour.core.flows.PhoneFile
import tv.cleanyour.core.flows.PhoneFiles

// Upgrade Your TV (Storage, files and apps): a file the person picked with the system's file picker,
// named and sized by its content provider and read as a stream. Only URIs the picker granted reach
// here; nothing else on the phone is read.
class AndroidPhoneFiles(context: Context) : PhoneFiles {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun describe(ref: String): PhoneFile? = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(ref)
            var name: String? = null
            var size = -1L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
            if (size < 0) size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (size <= 0) null else PhoneFile(name ?: uri.lastPathSegment ?: "file", size)
        }.getOrNull()
    }

    override fun open(ref: String): InputStream? = runCatching { resolver.openInputStream(Uri.parse(ref)) }.getOrNull()
}
