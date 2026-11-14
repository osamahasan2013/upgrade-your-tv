# Upgrade Your TV

Clean up and manage an Android TV or Google TV from your Android phone, over your home Wi-Fi.
No laptop, no cables, nothing uninstalled, and every change can be undone.

**Upgrade Your TV is based on the [Clean Your TV app](https://github.com/udhaykumarbala/cleanyourtv-app)**
(MIT) and keeps everything it does: it finds the TV, switches off adverts, tracking and the apps
nobody asked for using the community's tested lists, can install the
[Clean Your TV launcher](https://github.com/Gathin23/cleanyourtv) as a calm home screen on supported
TVs, and keeps a restore point for every change. Thank you to both projects and their contributors.

## What Upgrade Your TV adds

- **All apps**: every app on the TV, system apps included, with what is running right now, a search
  and filters (All, Running, Off), and a switch to turn any app off or back on. Each change goes
  through the same safe path as the rest of the app: it is journalled, it can be undone from the
  snackbar or the restore point, and nothing is ever deleted. Apps whose loss would break the TV or
  cut the app off from it (Wi-Fi, the remote, the TV inputs, Google Play services, the home screen
  in use, the debugging service) show a lock and the reason instead of a switch.

- **Storage, files and apps**: the TV's free space (internal and USB), files sent from the phone to the
  TV's Download folder and deleted again, and APKs installed on the TV from the phone. Apps you installed
  can be uninstalled from All apps.
- **Video background**: a short MP4 from the phone plays behind your apps on the new home screen, muted
  and looping, only while Home is on screen. The bundled home screen is the Clean Your TV launcher with
  [this patch](patches/launcher/0001-video-background.patch), signed with this project's key; a TV set up
  by the original app gets it through **Run Setup again**, which asks before it replaces the old one.

## Download

Get the APK from [Releases](https://github.com/osamahasan2013/upgrade-your-tv/releases/latest) and open it on your phone
(Android 9 or newer, with a screen lock, on the same Wi-Fi as the TV). It installs beside the
original Clean Your TV app and keeps its own TVs and restore points.

## Build it yourself

The **apk** workflow (Actions tab, Run workflow) builds a signed APK. Pushing a tag `v1.2.3`
builds it and publishes a Release, which the app's update check reads. Signing uses two
repository secrets, `KEYSTORE_B64` and `KEYSTORE_PASSWORD`; the first manual run without them makes
a key and offers it as an artifact to save as those secrets.

Locally: JDK 21 and the Android SDK, then `./gradlew :app:assembleFullDebug`.

## Licence

MIT. See [LICENSE](LICENSE) and [THIRD-PARTY-NOTICES](THIRD-PARTY-NOTICES); the original
Clean Your TV notices are kept as they are. The original README is in
[docs/UPSTREAM-README.md](docs/UPSTREAM-README.md).

Upgrade Your TV by أسامة حسن دسوقي / Osama Hasan Desouky.
