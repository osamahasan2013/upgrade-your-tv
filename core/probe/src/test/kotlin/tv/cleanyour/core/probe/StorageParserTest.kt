package tv.cleanyour.core.probe

import org.junit.Test
import kotlin.test.assertEquals

// Storage, files and apps: `df -k` and the Download folder's `stat` lines as Android TV prints them.
class StorageParserTest {
    @Test fun internalAndOneUsbDriveListedOnce() {
        val df = """
            Filesystem      1K-blocks    Used Available Use% Mounted on
            /dev/root         2490376 2465432     24944 100% /
            tmpfs              958372    1036    957336   1% /dev
            /dev/block/dm-2  10913648 4718232   6195416  44% /data
            /data/media      10913648 4718232   6195416  44% /storage/emulated
            /dev/fuse        30553088 1048576  29504512   4% /mnt/media_rw/1A2B-3C4D
            /dev/fuse        30553088 1048576  29504512   4% /storage/1A2B-3C4D
        """.trimIndent()
        val v = StorageParser.parseDf(df.lineSequence())
        assertEquals(listOf(StorageVolume.Kind.INTERNAL, StorageVolume.Kind.EXTERNAL), v.map { it.kind })
        assertEquals(StorageVolume(StorageVolume.Kind.INTERNAL, "data", "/data", 10913648, 4718232, 6195416), v[0])
        assertEquals("1A2B-3C4D", v[1].id)
        assertEquals("/storage/1A2B-3C4D", v[1].mount)
    }

    @Test fun downloadsAreRegularFilesNewestFirst() {
        val lines = """
            regular file|1048576|1700000000|/sdcard/Download/movie.mp4
            directory|4096|1700000500|/sdcard/Download/sub
            regular file|2048|1700000900|/sdcard/Download/notes.txt
            regular file|x|1|/sdcard/Download/bad
        """.trimIndent()
        assertEquals(listOf(TvFile("notes.txt", 2048, 1700000900), TvFile("movie.mp4", 1048576, 1700000000)),
            StorageParser.parseDownloads(lines.lineSequence()))
    }
}
