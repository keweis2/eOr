#!/usr/bin/env python3
"""Prepare a contributor checkout using Python's standard library only."""
import argparse
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent


def property_groups(text):
    """Keep physical lines intact while reading Java properties continuations."""
    raw, logical = "", ""
    for line in text.splitlines(keepends=True):
        if not raw and line.lstrip().startswith(("#", "!")):
            yield line, line.rstrip("\r\n")
            continue
        raw += line
        content = line.rstrip("\r\n")
        logical += content.lstrip() if logical else content
        if (len(content) - len(content.rstrip("\\"))) % 2:
            logical = logical[:-1]
            continue
        yield raw, logical
        raw, logical = "", ""
    if raw:
        yield raw, logical


def unescape_property(value):
    def replace(match):
        token = match[1]
        return chr(int(token[1:], 16)) if token.startswith("u") else {
            "t": "\t", "n": "\n", "r": "\r", "f": "\f"
        }.get(token, token)
    value = re.sub(r"\\(u[0-9a-fA-F]{4}|.)", replace, value)
    return value.encode("utf-16", errors="surrogatepass").decode("utf-16")


def read_property(path, key):
    if not path.is_file():
        return None
    value = None
    for _, line in property_groups(path.read_bytes().decode("latin-1")):
        match = re.match(r"\s*" + re.escape(key) + r"(?:\s*[:=]\s*|\s+)(.*)", line)
        if match:
            value = unescape_property(match[1])
    return value


def escape_property(value):
    result = ""
    for char in str(value):
        if char in "\\:=#! ":
            result += "\\" + char
        elif ord(char) < 32 or ord(char) > 126:
            encoded = char.encode("utf-16-be")
            result += "".join(f"\\u{int.from_bytes(encoded[i:i+2], 'big'):04x}"
                              for i in range(0, len(encoded), 2))
        else:
            result += char
    return result


def write_local_properties(root, sdk, python):
    path = root / "local.properties"
    original = path.read_bytes().decode("latin-1") if path.exists() else ""
    newline = "\r\n" if "\r\n" in original else "\n"
    updates = {"sdk.dir": escape_property(sdk), "eor.python": escape_property(python)}
    lines = []
    for raw, logical in property_groups(original):
        match = re.match(r"\s*(sdk\.dir|eor\.python)(?:\s*[:=]|\s+)", logical)
        if match:
            key = match[1]
            if key in updates:
                lines.append(key + "=" + updates.pop(key) + newline)
        else:
            lines.append(raw)
    text = "".join(lines)
    if text and not text.endswith(("\n", "\r")):
        text += newline
    text += "".join(key + "=" + value + newline for key, value in updates.items())
    if text != original:
        path.write_bytes(text.encode("latin-1"))


def required_packages(root):
    build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
    def find(pattern):
        match = re.search(pattern, build)
        if not match:
            raise ValueError("Cannot read SDK versions from app/build.gradle.kts")
        return match[1]
    return ["platform-tools",
            "platforms;android-" + find(r"\bcompileSdk\s*=\s*(\d+)"),
            "build-tools;" + find(r'\bbuildToolsVersion\s*=\s*"([^"]+)"'),
            "ndk;" + find(r'\bndkVersion\s*=\s*"([^"]+)"'),
            "cmake;" + find(r'cmake\s*\{[^}]*\bversion\s*=\s*"([^"]+)"')]


def find_sdk(root, override=None):
    configured = (override or read_property(root / "local.properties", "sdk.dir")
                  or os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"))
    if configured:
        return Path(configured).expanduser().resolve()
    home = Path.home()
    candidates = [home / "Android/Sdk", home / "Library/Android/sdk",
                  Path(os.environ.get("LOCALAPPDATA", home / "AppData/Local")) / "Android/Sdk"]
    return next((p.resolve() for p in candidates if p.is_dir()), None)


def java_major(output):
    match = re.search(r'(?:openjdk|java)(?:\s+version)?\s+"?(\d+)(?:\.(\d+))?', output, re.I)
    if not match:
        return 0
    return int(match[2] if match[1] == "1" else match[1])


def find_java(root, override=None):
    suffix = ".exe" if os.name == "nt" else ""
    candidates = [override] if override else [os.environ.get("JAVA_HOME"),
        read_property(root / ".gradle/config.properties", "java.home")]
    if not override:
        on_path = shutil.which("java")
        if on_path:
            candidates.append(Path(on_path).resolve().parent.parent)
        studio = os.environ.get("ANDROID_STUDIO_HOME")
        if studio:
            candidates.append(Path(studio) / "jbr")
        candidates += [Path("/opt/android-studio/jbr"), Path("/usr/local/android-studio/jbr"),
            Path.home() / "android-studio/jbr",
            Path("/Applications/Android Studio.app/Contents/jbr/Contents/Home"),
            Path.home() / "Applications/Android Studio.app/Contents/jbr/Contents/Home",
            Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Android/Android Studio/jbr",
            Path(os.environ.get("LOCALAPPDATA", Path.home())) / "Programs/Android Studio/jbr"]
    for home in candidates:
        if not home:
            continue
        home = Path(home).expanduser()
        java = home / "bin" / ("java" + suffix)
        if java.is_file():
            try:
                result = subprocess.run([str(java), "-version"], capture_output=True, text=True, timeout=15)
                if result.returncode == 0 and 17 <= java_major(result.stdout + result.stderr) <= 25:
                    return home.resolve()
            except (OSError, subprocess.TimeoutExpired):
                continue
    return None


def host_problem():
    system, arch = platform.system(), platform.machine().lower()
    if system == "Linux":
        if arch not in ("x86_64", "amd64"):
            return "NDK 26 needs a Linux x64 host. Use an x64 Linux machine or build environment."
        if not any(shutil.which(tool) for tool in ("cc", "gcc", "clang")):
            return "Install host C/C++ tools: build-essential (Debian/Ubuntu), or gcc and gcc-c++ (Fedora)."
    elif system == "Darwin":
        result = subprocess.run(["/usr/bin/xcode-select", "-p"], capture_output=True)
        if result.returncode:
            return "Install Xcode Command Line Tools: xcode-select --install"
    elif system == "Windows":
        program_files = Path(os.environ.get("ProgramFiles(x86)", "C:/Program Files (x86)"))
        vswhere = program_files / "Microsoft Visual Studio/Installer/vswhere.exe"
        component = "Microsoft.VisualStudio.Component.VC.Tools." + (
            "ARM64" if arch in ("arm64", "aarch64") else "x86.x64")
        found = False
        if vswhere.is_file():
            result = subprocess.run([str(vswhere), "-latest", "-products", "*", "-requires", component,
                                     "-property", "installationPath"], capture_output=True, text=True)
            found = result.returncode == 0 and bool(result.stdout.strip())
        if not found or not (program_files / "Windows Kits/10/Include").is_dir():
            return ("Install Visual Studio Build Tools: Desktop development with C++, MSVC tools "
                    "for your host architecture, and a Windows SDK.")
    else:
        return f"Unsupported build host: {system}. Use Windows, macOS, or Linux x64."
    return None


def find_sdkmanager(sdk):
    name = "sdkmanager.bat" if os.name == "nt" else "sdkmanager"
    candidates = [sdk / "cmdline-tools/latest/bin" / name]
    candidates += sorted((sdk / "cmdline-tools").glob("*/bin/" + name), reverse=True)
    return next((p for p in candidates if p.is_file()), None)


def missing_packages(sdk, packages):
    return [p for p in packages if not (sdk.joinpath(*p.split(";")) / "package.xml").is_file()]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check without downloads or configuration writes")
    parser.add_argument("--sdk", help="Android SDK directory (otherwise auto-detected)")
    parser.add_argument("--java-home", help="JDK 17+ used to launch Gradle; the build JDK 25 is provisioned automatically")
    parser.add_argument("--build", action="store_true", help="Also build the full debug APK")
    args = parser.parse_args(argv)
    if args.check and args.build:
        parser.error("--check cannot be combined with --build")
    if sys.version_info < (3, 12):
        raise ValueError("Python 3.12+ is required. Install it and run this command again.")

    problems = []
    sdk, java = find_sdk(ROOT, args.sdk), find_java(ROOT, args.java_home)
    problem = host_problem()
    if problem:
        problems.append(problem)
    if not sdk or not sdk.is_dir():
        problems.append("Install Android Studio and finish its SDK setup, or pass --sdk /path/to/Android/Sdk.")
    if not java:
        problems.append("Cannot find JDK 17–25. Pass --java-home pointing to Android Studio's bundled jbr directory.")
    packages = required_packages(ROOT)
    missing = missing_packages(sdk, packages) if sdk and sdk.is_dir() else packages
    manager = find_sdkmanager(sdk) if sdk else None
    if missing and not manager:
        problems.append("Install Android SDK Command-line Tools (latest) in Android Studio → SDK Manager → SDK Tools.")
    if args.check and missing:
        problems.append("Missing SDK packages: " + ", ".join(missing) + ". Run setup without --check to install them.")
    if problems:
        raise ValueError("\n".join(problems))

    env = os.environ.copy()
    env.update(JAVA_HOME=str(java), ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk))
    print(f"Python: {sys.executable}\nLauncher JDK: {java}\nAndroid SDK: {sdk}", flush=True)
    if not args.check:
        write_local_properties(ROOT, sdk, sys.executable)
        if missing:
            print("Installing SDK packages; review any license prompts below.", flush=True)
            subprocess.run([str(manager), f"--sdk_root={sdk}", "--install", *missing], env=env, check=True)
            remaining = missing_packages(sdk, packages)
            if remaining:
                raise ValueError("SDK installation incomplete: " + ", ".join(remaining)
                                 + ". Accept the SDK licenses and rerun setup.")
    ndk = sdk.joinpath(*next(p for p in packages if p.startswith("ndk;")).split(";"))
    banner = [sys.executable, str(ROOT / "tools/setup_3ds_banner.py"), "--ndk", str(ndk)]
    subprocess.run(banner + (["--check"] if args.check else []), cwd=ROOT, env=env, check=True)
    if args.check:
        print("Prerequisites checked. Gradle will reuse or provision Java 25 on the next build.")
        return
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    task = ":app:assembleFullDebug" if args.build else "help"
    subprocess.run([str(wrapper), task, "--console=plain"], cwd=ROOT, env=env, check=True)
    print("Setup complete. Open this folder in Android Studio and select the fullDebug build variant.")
    print("For direct Gradle commands, set JAVA_HOME to the launcher JDK above, "
          "or rerun this setup command with --build.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f"Setup failed: {error}", file=sys.stderr)
        sys.exit(1)
