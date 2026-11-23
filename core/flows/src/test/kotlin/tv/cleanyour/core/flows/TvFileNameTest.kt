package tv.cleanyour.core.flows

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tv.cleanyour.core.model.FILE_NAME_RE

// Storage, files and apps: every name sent to the TV passes RemotePath's FILE_NAME_RE.
class TvFileNameTest {
    private val now = 1_700_000_000_000L

    @Test fun keepsAsciiNamesAndTheirExtension() {
        assertEquals("Holiday_2026.mp4", StorageMachine.tvFileName("Holiday 2026.mp4", now))
        assertEquals("report-final.pdf", StorageMachine.tvFileName("report-final.pdf", now))
    }

    @Test fun nonAsciiNamesGetADatedName() {
        val n = StorageMachine.tvFileName("فيلم العيد.mkv", now)
        assertTrue(n.startsWith("file-") && n.endsWith(".mkv"), n)
        assertTrue(FILE_NAME_RE.matches(n), n)
    }

    @Test fun everyNameFitsTheRule() {
        for (raw in listOf("..hidden", "a", "x".repeat(200) + ".jpeg", "we'ird \"name\";rm -rf.txt", ".mp3", "صورة.png", "-dash.apk")) {
            val n = StorageMachine.tvFileName(raw, now)
            assertTrue(FILE_NAME_RE.matches(n) && !n.contains(".."), "$raw -> $n")
        }
    }

    @Test fun duplicatesInOneSendAreNumbered() {
        assertEquals("a-2.txt", StorageMachine.uniqueName("a.txt", setOf("a.txt")))
        assertEquals("a-3.txt", StorageMachine.uniqueName("a.txt", setOf("a.txt", "a-2.txt")))
    }
}
