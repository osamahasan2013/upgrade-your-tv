package tv.cleanyour.core.probe

import org.junit.Test
import kotlin.test.assertEquals
import tv.cleanyour.core.model.PackageName

// All apps: `ps -A -o ARGS` read into the packages with a process. Native daemons, kernel threads
// and the header never count; a secondary process (":name") counts for its package.
class RunningAppsTest {
    @Test fun appProcessesOnlyWithSecondaryProcessesFolded() {
        val ps = """
            ARGS
            init second_stage
            [kthreadd]
            [kworker/0:1]
            /system/bin/surfaceflinger
            system_server
            zygote64
            com.android.systemui
            com.google.android.gms.persistent
            com.google.android.gms:persistent
            com.google.android.youtube.tv
            com.tcl.tvweishi:remote
            /data/local/tmp/cleanyourtv/watchdog cleanyourtv-rollback-abc
            sh -c ps -A -o ARGS
        """.trimIndent()
        assertEquals(
            setOf("com.android.systemui", "com.google.android.gms.persistent", "com.google.android.gms", "com.google.android.youtube.tv", "com.tcl.tvweishi")
                .map(::PackageName).toSet(),
            RunningParser.parse(ps.lineSequence()),
        )
    }

    @Test fun emptyOutputIsNoProcesses() {
        assertEquals(emptySet(), RunningParser.parse(sequenceOf("ARGS", "")))
    }
}
