package tv.cleanyour.core.probe

import tv.cleanyour.core.model.PACKAGE_RE
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.session.TvSession

// All apps screen: the packages that have a process running on the TV right now. Not part of the
// frozen Prober (section 5.5), like LaunchableApps. One read-only call, the existing constant
// ProbeScripts.PS_ARGS ("ps -A -o ARGS"), so no new shell line reaches the TV. Fails with
// AdbException or ProbeException like the Prober; a caller that cannot tell shows no running marks.
fun interface RunningApps {
    suspend fun runningPackages(session: TvSession): Set<PackageName>
}

// `ps -A -o ARGS` prints a header ("ARGS"), then one command line per process. An app's process is
// named after its package ("com.google.android.youtube.tv"), a secondary process adds ":name"
// ("com.google.android.gms:persistent"). Native daemons start with a path or have no dot
// ("/system/bin/surfaceflinger", "system_server", "[kworker/0:1]") and never match PACKAGE_RE.
object RunningParser {
    fun parse(lines: Sequence<String>): Set<PackageName> {
        val out = LinkedHashSet<PackageName>()
        for (raw in lines) {
            val first = raw.trim().substringBefore(' ').substringBefore(':')
            if (first.isEmpty() || first.length > 255 || !PACKAGE_RE.matches(first)) continue
            out += PackageName(first)
        }
        return out
    }
}
