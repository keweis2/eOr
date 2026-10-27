#!/usr/bin/env python3
"""Install/check the Rust prerequisites for the Android banner renderer."""
import argparse
import os
import platform
from pathlib import Path
import subprocess
import sys
import tempfile
import urllib.request
from banner_build_support import ndk_tools, rust_tool


def main():
    if sys.version_info < (3, 12):
        raise SystemExit("Python 3.12+ is required. Run this script with a newer Python.")
    import tomllib

    root = Path(__file__).resolve().parent.parent
    toolchain = tomllib.loads((root / "rust-toolchain.toml").read_text(encoding="utf-8"))["toolchain"]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check prerequisites without installing anything")
    parser.add_argument("--ndk", type=Path, help="Override the Android NDK location")
    args = parser.parse_args()

    # Match the version used by Gradle; avoid maintaining a second version constant.
    import re
    gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
    ndk_version = re.search(r'ndkVersion\s*=\s*"([^"]+)"', gradle).group(1)
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    properties = root / "local.properties"
    if properties.exists():
        for line in properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                sdk = re.sub(r"\\(.)", r"\1", line.split("=", 1)[1])
    if not sdk and not args.ndk:
        raise SystemExit("Set sdk.dir in local.properties (Android Studio does this), set ANDROID_HOME, or pass --ndk.")
    ndk = args.ndk or Path(sdk) / "ndk" / ndk_version
    try:
        ndk_tools(ndk)
    except ValueError as error:
        raise SystemExit(f"{error}\nInstall NDK {ndk_version} in Android Studio SDK Manager, "
                         f'or run: sdkmanager "ndk;{ndk_version}"') from error

    rustup = rust_tool("rustup")
    if not Path(rustup).is_file():
        if args.check:
            raise SystemExit("Rustup is missing. Run: python3 tools/setup_3ds_banner.py")
        print("Installing rustup from the official Rust installer (no shell profile changes).", flush=True)
        with tempfile.TemporaryDirectory() as directory:
            if os.name == "nt":
                architecture = platform.machine().lower()
                host = {"amd64": "x86_64", "x86_64": "x86_64", "arm64": "aarch64", "aarch64": "aarch64"}.get(architecture)
                if host is None:
                    raise SystemExit(f"Unsupported Windows architecture: {architecture}. Install rustup manually.")
                installer = Path(directory) / "rustup-init.exe"
                url = f"https://static.rust-lang.org/rustup/dist/{host}-pc-windows-msvc/rustup-init.exe"
                command = [str(installer)]
            else:
                installer = Path(directory) / "rustup-init.sh"
                url = "https://sh.rustup.rs"
                command = ["sh", str(installer)]
            urllib.request.urlretrieve(url, installer)
            subprocess.run(command + ["-y", "--no-modify-path", "--default-toolchain", "none"], check=True)
        rustup = rust_tool("rustup")

    channel = toolchain["channel"]
    if not args.check:
        subprocess.run([rustup, "toolchain", "install", channel, "--profile", toolchain["profile"],
                        "--target", ",".join(toolchain["targets"])], check=True)
    # `rustup run` checks the pinned compiler without changing the user's default.
    subprocess.run([rustup, "run", channel, "rustc", "--version"], check=True)
    installed = subprocess.check_output([rustup, "target", "list", "--installed", "--toolchain", channel], encoding="utf-8").splitlines()
    missing = set(toolchain["targets"]) - set(installed)
    if missing:
        raise SystemExit(f"Missing Android targets: {', '.join(sorted(missing))}. Run: python3 tools/setup_3ds_banner.py")
    print(f"Banner build prerequisites ready: Python {sys.version.split()[0]}, Rust {channel}, NDK {ndk_version}.")
    print("Build with: " + (".\\gradlew.bat" if os.name == "nt" else "./gradlew") + " assembleFullDebug")


if __name__ == "__main__":
    try:
        main()
    except (subprocess.CalledProcessError, OSError) as error:
        raise SystemExit(f"Banner setup failed: {error}") from error
