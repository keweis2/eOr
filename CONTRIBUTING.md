# Contributing to eOr

## First checkout

Install these once:

- A current Android Studio release supporting Android Gradle Plugin 9.5. Finish
  its SDK setup, then enable **Android SDK Command-line Tools (latest)** under
  **Tools → SDK Manager → SDK Tools**.
- Python **3.12 or newer**. Check with `python3 --version` on Linux/macOS, or
  `py -3 --version` on Windows.
- The host compiler tools below, which Rust uses for build scripts.

| Host | Compiler prerequisites |
| --- | --- |
| Ubuntu/Debian x64 | `sudo apt install build-essential` |
| Fedora x64 | `sudo dnf install gcc gcc-c++` |
| macOS Intel/Apple Silicon | `xcode-select --install` |
| Windows | Visual Studio Build Tools, **Desktop development with C++**, MSVC tools, and a Windows SDK. On ARM64, also select the ARM64 MSVC tools. |

Clone the repository, open a terminal in its root, and run:

```sh
# Linux / macOS
python3 tools/setup.py
```

```powershell
# Windows PowerShell
py -3 tools/setup.py
```

Setup discovers the SDK and a launcher JDK (including Android Studio's bundled
JDK), installs missing SDK packages, and installs the pinned Rust compiler and
Android targets. Review and accept SDK license prompts when requested. It then
runs Gradle to provision Java 25 and verify project configuration.

It records the SDK and the Python interpreter used for setup in the ignored
`local.properties`, preserving other entries. This lets Android Studio find the
same Python interpreter even when its PATH differs from your terminal's.

**Java 25 downloads automatically** when no suitable installation exists. The
download URLs cover Windows, macOS, and Linux on x64 and ARM64. The pinned Android
NDK currently requires **x64 for Linux native builds**. Full application builds
are verified on Linux x64; Windows and macOS still need full build verification.

The first run needs internet access and several GB of disk space. Downloads are
kept in your Android SDK, Gradle, and Rust caches for reuse.

## Open in Android Studio

Open the repository root, allow Gradle sync to complete, and select **fullDebug**
for `app` in **Build Variants**. Use **liteDebug** when working on the low-power
variant. Select your device and run the `app` configuration.

The committed Gradle daemon criteria select Java 25 for both Studio and terminal
builds. Studio's bundled JDK can launch Gradle; you do not need to manually
download Java 25 or install a separate Gradle distribution.

## Setup options

Use `python3` below on Linux/macOS, or `py -3` on Windows:

```sh
# Set up and build a full debug APK in one command.
python3 tools/setup.py --build

# Check local prerequisites without downloads or configuration writes.
python3 tools/setup.py --check

# Select nonstandard installation locations. Quote paths containing spaces.
python3 tools/setup.py --sdk "/path/to/Android SDK" --java-home "/path/to/jdk"
```

SDK discovery checks `--sdk`, `local.properties`, `ANDROID_HOME`,
`ANDROID_SDK_ROOT`, and standard Android Studio SDK locations, in that order.
`--java-home` needs a JDK from 17 through 25 to launch Gradle. Setup otherwise
checks `JAVA_HOME`, Studio's local JDK setting, PATH, and common Studio locations.
The build itself always uses Java 25, provisioning it when necessary. `--check`
checks prerequisites; the next Gradle invocation can still need a JDK download.

For direct terminal Gradle commands, make Java 17–25 available on PATH or set
`JAVA_HOME` to a compatible JDK. If your terminal cannot find Java, use
`tools/setup.py --build`, which discovers Studio's bundled JDK for you.
For example, in Windows PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleFullDebug
```

The SDK package versions come from `app/build.gradle.kts`; Rust and its targets
come from `rust-toolchain.toml`. Python requires no third-party packages.

## Build and test

On Windows, replace `./gradlew` with `.\gradlew.bat` and `python3` with `py -3`.

```sh
./gradlew :app:assembleFullDebug
./gradlew :app:assembleLiteDebug
./gradlew :app:testFullDebugUnitTest
python3 -m unittest discover -s tools/tests
```

Debug APKs are in `app/build/outputs/apk/full/debug/` and
`app/build/outputs/apk/lite/debug/`. JVM test reports are in
`app/build/reports/tests/testFullDebugUnitTest/`. Some ROM-fixture tests are
skipped unless local fixtures are configured; see the
[renderer guide](app/src/main/rust/three_ds_banner/README.md).

Configuration caching, build caching, and incremental compilation are enabled.
Use ordinary Build/Run operations and keep existing outputs between edits.
Studio and terminal builds under the same account share local caches. Each
contributor has their own caches on their computer.

## Credentials and local settings

Developer credentials are optional for building and running tests. For
ScreenScraper integration work, put credentials you are authorized to use in
`local.properties` as `SS_DEV_ID` and `SS_DEV_PASSWORD`. Keep local settings and
credentials out of commits.

Setup records Python as `eor.python` in `local.properties`. For a temporary
override, use `-PeorPython=/path/to/python` or the `EOR_PYTHON` environment
variable, in that precedence order. Rerunning setup refreshes the saved path.
The [renderer guide](app/src/main/rust/three_ds_banner/README.md) covers Rust and
native renderer troubleshooting.

## Updating Java provisioning

The root `build.gradle.kts` pins vendor download URLs. Update those URLs and the
Java version together when upgrading, run `./gradlew updateDaemonJvm`, and commit
the regenerated `gradle/gradle-daemon-jvm.properties`. Those committed URLs let
Gradle download the build JDK before evaluating project plugins. See
[Gradle's daemon JVM documentation](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:daemon_jvm_criteria).

## Single-screen and dual-screen UI

`ui/app/AppUi.kt` is the UI boundary. `MainActivity` owns Android lifecycle,
permissions, display management and the application theme. `AppUi` owns the shared
startup screen, update banners, system Back handling and navigation controller.
It selects `ui/singlescreen/SingleScreenUi.kt` or `ui/dualscreen/DualScreenUi.kt`.

Both entry points currently reuse `ui/navigation/AppNavGraph.kt` and the existing
screens. This is an incremental boundary, ahead of the dual-screen redesign:
put new dual-screen layouts and coordination in `ui/dualscreen`, without adding
device branches to the existing single-screen screens. Share repositories and
use cases; extract shared UI only when both interfaces need it. The existing
Home route/rendering separation remains in place.

`DualScreenManager.uiMode` selects the interface from the enabled preference and
an available second display. It is separate from `active`, which describes
whether artwork is presented right now. Temporarily hiding artwork for gameplay
must not switch interfaces. Disabling dual-screen mode or losing the second
display selects the existing single-screen interface. The existing artwork and
game-session composition locals remain compatibility adapters for the shared
screens until the dual-screen UI is redesigned.
