package tv.cleanyour.core.trust

import tv.cleanyour.core.model.LauncherArtifact
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.store.BlobStore

// Trust anchors compiled into the app. Changing one is a reviewed code change, never a data file.
object TrustRoots {
    // Root of the module index chain: signs keys.json only (KeysFile). Release key K2R, generated
    // 2026-10-20 for 0.1.0; it replaced the development keys of K2, which never signed a public
    // build. The private half stays offline with the maintainer (docs/history/DECISIONS-W5.md, K2R).
    const val INDEX_ROOT_PUBLIC_KEY = "RWTBm+H9rjMuaX944TBay7uDMxQEC8Q+E8yF1p7hyGl7Fa+8bzBKE4io"   // key id 692E33AEFDE19BC1

    // The sequence of the keys.json bundled with this build: an older keys file is refused.
    const val BUNDLED_KEYS_SEQUENCE = 1L

    val indexRoot: MinisignKey get() = Ed25519Minisign.parsePublicKey(INDEX_ROOT_PUBLIC_KEY)

    fun indexVerifier(): MinisignIndexVerifier = MinisignIndexVerifier(indexRoot, keysFloor = BUNDLED_KEYS_SEQUENCE)
}

// The launcher APK bundled in the phone app (D13, Q1). Upgrade Your TV: upstream
// github.com/Gathin23/cleanyourtv at main after v3.0.0, commit b06ab21 (third_party/cleanyourtv),
// launcher 3.0 with patches/launcher (the video background; dot type, sidebar, Continue card), versionCode 1006 "3.0-uytv6" (themes, Style page, the Style button on Home), built by
// .github/workflows/launcher.yml and signed with this repo's key (the phone app's). A TV that has the
// original launcher, signed with the Clean Your TV launcher key, gets it through Setup's key
// replacement (D36). The verifyLauncherPin task (part of :core:trust check and of :android:platform
// preBuild) fails the build when the asset differs.
object PinnedLauncher {
    const val ASSET_NAME = "cleanyourtv-launcher.apk"
    const val UPSTREAM_COMMIT = "b06ab21105c49900cb1ed76b24414dba1e21d0db"
    const val PACKAGE = "com.gathin.tvhome"
    const val VERSION_CODE = 1006L
    const val VERSION_NAME = "3.0-uytv6"
    const val SIZE = 1_250_396L
    const val SHA256 = "cebcf87608de62de579bb47b7efd9bdce529806bcd1754ba15e50573c83da973"
    // SHA-256 of the signing certificate (DER), "CN=Upgrade Your TV", v2 and v3.
    const val SIGNER_SHA256 = "bc3d58326548f5b2da4bfec975586d8641bb3f27e33e6956a3f479976ba9176e"
    // The original Clean Your TV launcher key ("O=cleanyourtv, CN=Clean Your TV launcher"), which signs
    // the bundled TV curtain and the launcher on TVs set up by the original app.
    const val UPSTREAM_SIGNER_SHA256 = "576418154bec6505bfc709ccaa6ae85b6e09d98eb89f4a25147ca1b4b0505132"
}

// The TV curtain bundled in the phone app (RFC-CURTAIN-1): module :tv:curtain of this repo, built
// twice and compared, then signed with the launcher release key by scripts/build-curtain-on-forge.sh.
// Upgrade Your TV keeps that build and its signer, the original launcher key. The verifyCurtainPin task (part of :core:trust
// check and of :android:platform preBuild) fails the build when the asset differs.
object PinnedCurtain {
    const val ASSET_NAME = "cleanyourtv-curtain.apk"
    const val PACKAGE = "tv.cleanyour.curtain"
    const val ACTIVITY = ".CurtainActivity"
    const val RECEIVER = ".CueReceiver"
    const val VERSION_CODE = 1L
    const val VERSION_NAME = "0.1"
    const val SIZE = 65_955L
    const val SHA256 = "eb21e112c97121a3ed7f4db1c7fbf2169bc172a14bb3f8ca781079de4651662e"
    const val SIGNER_SHA256 = PinnedLauncher.UPSTREAM_SIGNER_SHA256   // still the original launcher key
}

// LauncherPin over the BlobStore's bundled asset. bundled() refuses an asset whose bytes are not the
// pinned ones, so a swapped asset can never be offered as the launcher.
class BundledLauncherPin(private val blobs: BlobStore) : LauncherPin {
    override fun bundled(): LauncherArtifact {
        val blob = blobs.bundled(PinnedLauncher.ASSET_NAME)
        check(blob.sha256.hex == PinnedLauncher.SHA256 && blob.size == PinnedLauncher.SIZE) {
            "bundled launcher does not match its pin (sha256 ${blob.sha256.hex}, ${blob.size} bytes)"
        }
        return LauncherArtifact(
            pkg = PackageName(PinnedLauncher.PACKAGE),
            versionCode = PinnedLauncher.VERSION_CODE,
            versionName = PinnedLauncher.VERSION_NAME,
            sha256 = Sha256(PinnedLauncher.SHA256),
            signer = Sha256(PinnedLauncher.SIGNER_SHA256),
            blob = blob,
        )
    }
}

// CurtainPin over the BlobStore's bundled asset, refusing an asset whose bytes are not the pinned ones.
class BundledCurtainPin(private val blobs: BlobStore) : CurtainPin {
    override fun bundled(): LauncherArtifact {
        val blob = blobs.bundled(PinnedCurtain.ASSET_NAME)
        check(blob.sha256.hex == PinnedCurtain.SHA256 && blob.size == PinnedCurtain.SIZE) {
            "bundled curtain does not match its pin (sha256 ${blob.sha256.hex}, ${blob.size} bytes)"
        }
        return LauncherArtifact(
            pkg = PackageName(PinnedCurtain.PACKAGE),
            versionCode = PinnedCurtain.VERSION_CODE,
            versionName = PinnedCurtain.VERSION_NAME,
            sha256 = Sha256(PinnedCurtain.SHA256),
            signer = Sha256(PinnedCurtain.SIGNER_SHA256),
            blob = blob,
        )
    }
}
