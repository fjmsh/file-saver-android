# DropFiler

An Android app that saves copies of files to folders on your phone, using the Share menu
or **Open with**.

DropFiler is a separately branded fork of [Phone Saver](https://github.com/ScreamingHawk/phone-saver),
originally developed by Michael Standen (ScreamingHawk). See [Credits](#credits).

| | |
| --- | --- |
| App name | DropFiler |
| Application ID | `fjm.sh.dropfiler` |
| Developer | [fjmsh](https://github.com/fjmsh) · [fjm.dev](https://fjm.dev) · [fjm.sh](https://fjm.sh) |
| Source | this repository (currently `fjmsh/file-saver-android`; it is planned to become `fjmsh/DropFiler-Android`) |

DropFiler is not (yet) published on Google Play or F-Droid. The store listings for
`link.standen.michael.phonesaver` belong to the original Phone Saver app.

## Description

Ever hit that share button and really wanted to just store the file on your phone?
That meme that you send to your friends that would be great in your reaction image folder.
That screenshot of that Snapchat that you want to hide in a secret folder.

Well look no further!

Set up any number of locations on your mobile, and save to them with a single click when using
Android's share feature, or by choosing **Open with → DropFiler** in a file manager.

## Features

* Select any number of locations to save to
* Works straight away: the built-in **DropFiler** folder (`/DropFiler`) is always available
  and cannot be removed. It can be hidden in Settings ("Hide "DropFiler" save location")
  while you have other destinations; with no other destination it is always used
* Share (single or multiple files, text and links) **and** "Open with" for files
* Save on a single click (saving is automatic when only one location is configured)
* Direct Share targets for your saved locations
* Configure whether or not gallery and music apps are notified of saves
* Choose from a range of duplicate file handling options
* A notification with the result of every save (saved / not saved)
* Create new folders while choosing a destination
* Reorder your destinations (drag the handle, or long-press a row); the share sheet uses the same order
* If a destination folder was deleted or moved, you are asked to pick another one
* Light and dark themes, with optional dynamic (Material You) colors on Android 12+
* Automated bug reports for requesting support for file types

## "Open with DropFiler"

Besides the Share menu, DropFiler is offered as an **Open with** app for files that other
apps (e.g. file managers) open with a standard Android `ACTION_VIEW` intent.

* "Open with" in DropFiler means **save a copy** of the file to one of your destinations.
  DropFiler does not display, play, edit or run files, and never modifies, moves or deletes
  the original.
* It uses the same destination sheet, filename handling, duplicate-file setting and result
  notification as sharing a single file.
* Files are read through the `content://` URI and the temporary read permission granted by
  the opening app. `file://` URIs are accepted as a best effort only (they are rare on
  Android 7+ and use the existing legacy storage permission). Web links are not handled.
* If the file cannot be read (no permission granted, the file was deleted, a folder was
  passed, the provider refused access), DropFiler says so and saves nothing.

Limitations: DropFiler only appears where the opening app uses a standard implicit
`ACTION_VIEW` intent with a MIME type and a readable URI. Apps that open files in their own
viewer, use proprietary actions, or do not grant access to the file will not list DropFiler,
and DropFiler cannot change that. Because it is not a viewer, avoid choosing it as the
**default** ("Always") app for a file type; you can clear that in Android's app settings
(Open by default → Clear defaults).

## Un-Features

* No sign up
* No requests to rate the app
* No trail periods
* No paid upgrades

## User interface: Miuix XML

Since version 6.0 the interface follows the Xiaomi HyperOS design language using
[Miuix XML (HiMiuix)](https://github.com/fjmsh/miuix-xml), a traditional Android View/XML
component library. The app does **not** use Jetpack Compose.

Miuix XML components used:

| Screen | Components |
| --- | --- |
| All screens | `MiuixAppBar` (large collapsing title), `SpringBackLayout` (Miuix over-scroll), `MiuixRecyclerFillView` / `MiuixFillView`, `fragment.Fragment` / `fragment.PreferenceFragmentCompat` |
| Destinations, share sheet, folder picker | `MiuixCardView` group cards, `ShadowHelper` press feedback, Miuix text / divider styles |
| Settings | `MiuixPreferenceCategory`, `MiuixSwitchPreference`, `MiuixDropDownPreference`, `MiuixPreference` |
| Dialogs | `MiuixAlertDialog` (change log, remove destination confirmation) |

Small app-side pieces follow the same design language where the library has no
equivalent: the rounded "Add destination" button, the share destination bottom sheet and
the destination row layout (`app/src/main/res/layout/fs_item_row.xml`).

### Integration

The library is included as a git submodule (`miuix-xml/`) and built as the Gradle module
`:HiMiuix`, as recommended by the library. Clone with submodules:

```shell
git clone --recurse-submodules https://github.com/fjmsh/file-saver-android.git
# or, in an existing clone
git submodule update --init
```

The submodule is pinned to a specific commit, so builds are reproducible.

### Blur

Miuix XML can use Xiaomi's private View blur. DropFiler only allows it when the device
provides those methods **and** Android reports that blur is enabled
(`WindowManager.isCrossWindowBlurEnabled()`, Android 12+). Everywhere else, including all
non-Xiaomi devices, the app bar uses a plain surface color. Navigation never depends on
blur. See `util/BlurSupport.kt`.

### Motion

Animations are short `ValueAnimator`/window animations. They follow the system animator
duration scale, so they are disabled when the user turns animations off
(Settings → Accessibility → Remove animations).

## Building

Requirements:

* JDK 21 (the Gradle toolchain targets Java 17 bytecode for the app, Java 21 for Miuix XML)
* Android SDK with platform **android-36** and build-tools 36 (only used to *compile*)
* Internet access to `google()` and Maven Central on first build

```shell
./gradlew assembleDebug          # build app/build/outputs/apk/debug/app-debug.apk
./gradlew lintDebug              # Android lint
./gradlew testDebugUnitTest      # Robolectric tests on API 21-36
./gradlew testDebugUnitTest -ProbolectricSdks=28   # limit the API levels tested
```

Build stack: Android Gradle Plugin 8.13.2, Gradle 8.14.3 (wrapper), Kotlin 2.2.21,
AndroidX (appcompat 1.7.1, preference 1.2.1, recyclerview 1.4.0) and core library
desugaring (required by Miuix XML for `java.util.stream` below API 24).

## Supported Android versions

| | API level |
| --- | --- |
| Minimum (`minSdk`) | 21 (Android 5.0) |
| Target (`targetSdk`) | **28 (Android 9) — fixed, see below** |
| Compile (`compileSdk`) | 36 |

`minSdk` was raised from 15 to 21 because current AndroidX (appcompat 1.7) requires API 21.
Miuix XML declared `minSdk 29`, but an audit of its actual API usage (Android lint
`NewApi`/`InlinedApi` at minSdk 21) found only a handful of calls above API 21. Those were
fixed in the library (compat helpers, version checks, core library desugaring) so the
app keeps supporting Android 5.0 – 9 instead of dropping to Android 10+. Known library
limitation below API 26: a Miuix seek bar cannot have a minimum other than 0 (not used by
this app).

### Why targetSdk stays at 28

The app deliberately keeps `targetSdkVersion 28`. **Do not raise it.** Raising it to 29+
opts the app into scoped storage, which breaks saving to arbitrary folders with plain
`java.io.File` access — the core of how DropFiler works. `compileSdk` is higher only so
that current libraries compile; it does not change runtime behaviour.

## Installing alongside Phone Saver

DropFiler uses its own application ID, `fjm.sh.dropfiler`. To Android it is a **new,
separate app**: it installs next to the original Phone Saver (`link.standen.michael.phonesaver`)
instead of updating it, and has its own data, permissions and signing identity.

* Phone Saver's destinations and settings are **not** transferred; set up your destinations
  again in DropFiler. DropFiler does not try to read another app's private data.
* Files you saved earlier stay where they are; DropFiler can save to the same folders.
* Future DropFiler updates install over each other only while the application ID stays
  `fjm.sh.dropfiler`, the APKs are signed with the same key and `versionCode` increases.

## Launcher icon

The final DropFiler icon has not been supplied yet, so the app still uses the existing
launcher icon. To replace it, provide artwork and update these resources:

| Resource | Notes |
| --- | --- |
| `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` | Square/legacy icon: 48, 72, 96, 144, 192 px |
| `app/src/main/res/mipmap-{…}/ic_launcher_round.png` | Round icon (`android:roundIcon`), same sizes |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` | Not present yet. Add an `<adaptive-icon>` with `@drawable/ic_launcher_foreground` (108 dp, 66 dp safe zone) and a background drawable/color |
| `<monochrome>` in the adaptive icon (`mipmap-anydpi-v26`) | Themed icon for Android 13+; a single-color vector layer |
| `app/src/main/ic_launcher_web.png`, `playstore/` | 512 px store icon and listing artwork, still Phone Saver's |

The Credits screen shows `@mipmap/ic_launcher`, so it updates with the launcher icon.

## Storage compatibility

The storage architecture is unchanged: destinations are absolute folders below the legacy
external storage root (`Environment.getExternalStorageDirectory()`), files are written with
`java.io`, URLs are downloaded with `DownloadManager`, and "location select" uses the Storage
Access Framework (`ACTION_CREATE_DOCUMENT`) as before. The stored destination list
(`FOLDER_STORE` JSON file) and all preference keys/values are unchanged.

What targeting API 28 means on newer Android versions:

* **Android 6 – 9:** `READ/WRITE_EXTERNAL_STORAGE` are requested at runtime as before.
* **Android 10:** apps targeting API 28 run with legacy storage automatically
  (`requestLegacyExternalStorage` only matters for apps targeting 29).
* **Android 11+:** scoped storage is only enforced for apps targeting API 30+; an app
  targeting 28 keeps legacy access through the storage permissions. However the platform
  still protects some locations regardless of target: `Android/data` and `Android/obb`
  of other apps are not writable, and some OEM builds restrict the root of removable
  SD cards. Use the **location select** setting (system file picker) for SD cards.
* **Android 13+:** `READ_EXTERNAL_STORAGE`/`WRITE_EXTERNAL_STORAGE` still apply to apps
  targeting API 32 or lower, so the existing permission request keeps working.
* **Android 14+:** installing an APK with a target below API 23 is blocked; API 28 is not
  affected. Google Play requires a recent target for *new* app updates; this project
  intentionally does not meet that requirement (see above).
* `MANAGE_EXTERNAL_STORAGE` is **not** requested.

### Direct Share

* Android 6 – 9: `LocationChooserTargetService` (unchanged).
* Android 10+: destinations are also published as sharing shortcuts
  (`res/xml/shortcuts.xml`, `DirectShareHelper`), the supported mechanism since
  ChooserTargetService was deprecated (recent Android versions no longer query it).
  On some launchers they also appear when long-pressing the app icon and open the app.

## Testing

`./gradlew testDebugUnitTest` runs Robolectric tests (JVM, simulated Android framework) on
API 21, 23, 26, 28, 29, 30, 31, 33, 34, 35 and 36. They cover saving text, images, videos,
documents and multiple files, the three duplicate-file strategies, automatic saving with
a single destination, Direct Share (extra and sharing shortcut), unsupported content,
"Open with" (`ACTION_VIEW` resolution for content/file URIs and common MIME types, copying,
missing MIME types or names, duplicates, revoked grants, missing files, folders, web links),
existing settings and destinations being read unchanged, adding/removing destinations,
the folder picker, light/dark/dynamic themes, the blur fallback and accessibility state of
Miuix switches.

Robolectric is not a device: it does not exercise real storage permission enforcement,
the system share sheet, DownloadManager, MediaScanner, the SAF picker UI or OEM (HyperOS)
behaviour. Those need manual testing on devices. See [MIGRATION.md](MIGRATION.md).

## Feature requests and bugs

Please use the [issues of this repository](https://github.com/fjmsh/file-saver-android/issues).
For problems that also affect the original app, see
[Phone Saver's issues](https://github.com/ScreamingHawk/phone-saver/issues).

## Credits

DropFiler is a separately branded fork of Phone Saver, originally developed by
[Michael Standen](https://michael.standen.link) (ScreamingHawk). The original project is
available at https://github.com/ScreamingHawk/phone-saver. Original code remains subject
to its applicable [MIT License](LICENSE); the copyright notice in `LICENSE` is kept unchanged.

The user interface uses [Miuix XML / HiMiuix](https://github.com/fjmsh/miuix-xml) by HChenX,
licensed under the [GNU LGPL 2.1](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html).
It is included as a separate module (git submodule, `miuix-xml/`) with its own source
and licence, so it can be replaced or relinked independently of the app.

Other libraries: AndroidX, Gson, ckChangeLog and Kotlin (Apache 2.0).

### Thanks

Special thanks to Michael Standen and all [the contributors](https://github.com/ScreamingHawk/phone-saver/graphs/contributors)
of Phone Saver, including its translators.
