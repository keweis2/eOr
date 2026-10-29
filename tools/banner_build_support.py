"""Host-specific paths and compiler configuration for the Android banner build."""
import os
from pathlib import Path
import platform
import shlex
import shutil


def rust_tool(name):
    executable = name + (".exe" if os.name == "nt" else "")
    cargo_home = Path(os.environ.get("CARGO_HOME", Path.home() / ".cargo"))
    return shutil.which(executable) or str(cargo_home / "bin" / executable)


def ndk_tools(ndk, system=None):
    system = system or platform.system()
    host = {"Linux": "linux-x86_64", "Darwin": "darwin-x86_64", "Windows": "windows-x86_64"}.get(system)
    if host is None:
        raise ValueError(f"Unsupported NDK build host: {system}")
    directory = Path(ndk) / "toolchains/llvm/prebuilt" / host / "bin"
    suffix = ".exe" if system == "Windows" else ""
    clang, ar = directory / ("clang" + suffix), directory / ("llvm-ar" + suffix)
    for tool in (clang, ar):
        if not tool.is_file():
            raise ValueError(f"Missing NDK tool: {tool}. Install the NDK for {system} using Android Studio.")
    return clang, ar


def compiler_environment(env, target, android_target, clang, ar):
    env = env.copy()
    key = target.replace("-", "_")
    env["CARGO_TARGET_" + key.upper() + "_LINKER"] = str(clang)
    # Use clang directly instead of the NDK's shell/.cmd wrappers. Rust needs the
    # Android target explicitly when clang is used as its linker.
    flags = env.get("CARGO_ENCODED_RUSTFLAGS")
    if flags is None:
        flags = "\x1f".join(shlex.split(env.get("RUSTFLAGS", env.get("CARGO_TARGET_" + key.upper() + "_RUSTFLAGS", ""))))
    extra = "\x1f".join(["-C", "link-arg=--target=" + android_target])
    env["CARGO_ENCODED_RUSTFLAGS"] = flags + "\x1f" + extra if flags else extra
    env["CC_" + key] = str(clang)
    env["AR_" + key] = str(ar)
    env["CFLAGS_" + key] = (env.get("CFLAGS_" + key, "") + " --target=" + android_target).strip()
    return env
