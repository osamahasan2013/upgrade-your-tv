package tv.cleanyour.core.ops

import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PackageState

// Upgrade Your TV, All apps: the packages the TV itself needs to run, or that this app needs to reach
// the TV and put things back. Never switched off, not even when the person overrides the protected
// denylist (Denylist) for one app on the All apps screen. Everything else on the denylist (Google
// Play services, the Play Store, TalkBack, a TV input, a setup wizard, a vendor home screen...)
// may then go off, shown in red with its reason, after a confirmation.
//
// The renderer refuses a switch-off of these whatever the plan says (ShellRenderer), so this list is
// the floor under every other check. A short list on purpose: each entry is here because without it
// the TV stops starting, loses its picture or its remote, or can no longer be fixed from the phone.
object Critical {
    val exact: Map<String, String> = linkedMapOf(
        Denylist.FRAMEWORK to "it is the Android framework itself",
        "com.android.systemui" to "it draws the TV's screen, dialogs and system UI",
        "com.android.settings" to "it is the TV's Settings app, the way to switch debugging back on",
        "com.android.tv.settings" to "it is the TV's Settings app, the way to switch debugging back on",
        "com.droidlogic.tv.settings" to "it is the TV's Settings app, the way to switch debugging back on",
        "com.android.packageinstaller" to "it installs and removes apps",
        "com.google.android.packageinstaller" to "it installs and removes apps",
        "com.android.permissioncontroller" to "it manages app permissions",
        "com.google.android.permissioncontroller" to "it manages app permissions",
        "com.android.providers.settings" to "it stores the TV's settings",
        "com.android.providers.media" to "it manages the TV's storage",
        "com.android.providers.media.module" to "it manages the TV's storage",
        "com.google.android.providers.media.module" to "it manages the TV's storage",
        "com.android.shell" to "it is the shell this app talks to; without it nothing could be put back",
        "com.android.adbd" to "it is the debugging service this app talks to; without it nothing could be put back",
        "com.android.keychain" to "it keeps the TV's certificates",
        "com.android.se" to "it is a secure element service",
        "com.android.bluetooth" to "it pairs and talks to the remote",
        "com.google.android.tv.btservices" to "it pairs and talks to the remote",
        "com.droidlogic.ble" to "it pairs and talks to the remote",
        "com.droidlogic" to "it is a core service of this TV's chipset",
        "com.droidlogic.overlay" to "it is a core service of this TV's chipset",
    )

    val prefixes: Map<String, String> = linkedMapOf(
        "com.android.networkstack" to "it keeps the TV's network running, which this app needs",
        "com.android.wifi." to "it keeps the TV's Wi-Fi running, which this app needs",
        "com.android.providers.media." to "it manages the TV's storage",
    )

    const val REASON_PERSISTENT = "Android keeps it running at all times; the TV can stop working or starting without it"

    fun staticReason(pkg: String): String? {
        exact[pkg]?.let { return it }
        for ((prefix, reason) in prefixes) if (pkg.startsWith(prefix)) return reason
        return null
    }

    // The static list plus the package's own facts: a persistent app is part of the running system.
    fun reason(pkg: PackageName, state: PackageState?): String? {
        staticReason(pkg.value)?.let { return it }
        if (state?.persistent == true) return REASON_PERSISTENT
        return null
    }
}
