# On-device 3DS banner renderer

The APK bundles `libeor_banner.so` for arm64-v8a and armeabi-v7a. eOr reads the main
NCCH's ExeFS banner from decrypted .3ds/.cci or CIA content, decodes bounded CBMD/LZ11
in Kotlin, then calls this JNI library on Dispatchers.IO. Offscreen Vulkan renders
the authored scene at 1200×720, and the PNG is written atomically to the user's local
media folder. Scanning backfills older games even when their icons already exist;
opening a detail page also repairs missing cached banners. Room updates the page live.
There is no runtime download, desktop process, file upload, emulator dependency, or
pre-rendered game artwork in the APK.

Icons and banners are independent. Screenshots/videos retain priority. Valid cached
banners are reused; unsupported/absent/encrypted content or unavailable Vulkan leaves
the icon fallback. JNI catches parser/render errors and Rust unwinds, and GPU readback
has a 20-second timeout. Runtime rendering requires Android 7/API 24 and working Vulkan;
the rest of eOr still supports Android 6. The first implementation uses the common
CGFX scene and its composed frame-0 animation, with billboard compensation, a HOME Menu
camera profile and frontal lighting. Language-specific overlay scenes are not merged.

## Building

For a new checkout, use the [contributor setup guide](../../../../../CONTRIBUTING.md)
and run `python3 tools/setup.py` from the repository root (`py -3 tools/setup.py`
on Windows). It configures Python for Android Studio, installs missing SDK
packages, and calls the Rust setup below. `python3 tools/setup.py --check` checks
the complete contributor prerequisites.

With Python 3.12+ and the Android SDK configured by Android Studio, run from the repository root:

```sh
python3 tools/setup_3ds_banner.py
./gradlew assembleFullDebug
```

The setup script checks the NDK and installs rustup if needed, then installs the exact
Rust version and both Android targets declared in the root `rust-toolchain.toml`.
It leaves your default Rust toolchain and shell profile unchanged. On Linux/macOS,
rustup is downloaded from its official installer at https://sh.rustup.rs. On Windows,
the script downloads the official `rustup-init.exe` for x64 or ARM64. Windows builds
also need Visual Studio Build Tools with the C++ workload and Windows SDK for Rust's
host build scripts; macOS needs Xcode Command Line Tools (`xcode-select --install`).
See the [official Rust installation instructions](https://rust-lang.org/tools/install/).

On Windows, use Python 3.12+ with `python` available on PATH, then run in PowerShell:

```powershell
python tools/setup_3ds_banner.py
.\gradlew.bat assembleFullDebug
```

If Python is installed under another name or outside PATH, set `EOR_PYTHON` to its
executable path, or pass `-PeorPython=/path/to/python` to Gradle. This accepts an
executable path, not a command with arguments. For example, PowerShell users can set
`$env:EOR_PYTHON = 'C:\Python312\python.exe'` before building in that terminal.
The contributor setup saves its interpreter as `eor.python` in `local.properties`,
which Android Studio reads automatically. The `eorPython` Gradle property and
`EOR_PYTHON` environment variable take precedence over that saved path. Rust/Cargo is also discovered in
`CARGO_HOME/bin` even when it is absent from PATH.

The scripts select the Linux, macOS (Intel/Apple Silicon), or Windows NDK host tools
explicitly. Linux APK builds are verified locally. Windows and macOS tool selection
has automated coverage, but complete builds on those hosts have not yet been verified.

Install NDK 26.1.10909125 through Android Studio's SDK Manager if the script reports it
missing. SDK discovery uses `local.properties`, `ANDROID_HOME`, or `ANDROID_SDK_ROOT`;
`--ndk /path/to/ndk` overrides it for the setup check. Gradle still uses its configured SDK.
For a read-only prerequisite check, including in CI:

```sh
python3 tools/setup_3ds_banner.py --check
```

Run setup without `--check` in CI to install the pinned toolchain and targets. Cache
`CARGO_HOME` and `RUSTUP_HOME` between builds if desired. Build machines still need the
project's usual JDK and Android SDK; this script only sets up the banner prerequisites.
Rustup's toolchain file behavior is documented in the
[official rustup guide](https://rust-lang.github.io/rustup/overrides.html#the-toolchain-file).

Gradle invokes `tools/build_3ds_banner.py` before packaging. It downloads checksum-pinned
cgfx 0.1.0 source archives, applies the vertex-color shader correction below, and
cross-compiles using NDK clang. The Android build is independent of desktop tooling.
All generated files stay in `app/build`. `Cargo.lock` pins the Rust graph. Set `EOR_CARGO` to select a Cargo executable;
normal `CARGO_HOME`/`RUSTUP_HOME` overrides also work. No Rust toolchain is needed on the Thor.

The cgfx parser/renderer libraries are MIT OR Apache-2.0; eOr uses them under Apache-2.0.
The APK includes dependency source/license notices in `assets/3ds-banner-licenses.txt`.
Source: https://docs.rs/crate/cgfx-wgpu/0.1.0

## Shader correction

The pinned upstream vertex shader multiplies vertex colors by the material diffuse
color before the PICA texture-combiner stages. That diffuse color already participates
in fragment lighting. Materials such as MarioMat have black diffuse and use ambient
and specular terms, so the extra multiplication produces a silhouette. The build
script passes vertex colors through unchanged; authored material colors and textures
are preserved. Both archive checksums and the expected original shader line are
validated before the correction is applied.

## Verification

The JVM tests cover ROM section bounds, CIA content selection, LZ11 back-references,
invalid streams, independent real-banner decoding, icon preservation, background backfill,
and cache reuse/repair. An on-device acceptance check deletes the cached banner files,
starts eOr, and verifies that the PNGs are recreated directly from the ROMs, then displayed.
No game-derived fixtures are committed; optional fixtures use `EOR_TEST_3DS_DIR` and
`EOR_TEST_3DS_CGFX` for local regression tests.
