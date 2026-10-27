"""Run with: python3 -m unittest discover -s tools/tests"""
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from banner_build_support import compiler_environment, ndk_tools


class BannerBuildSupportTest(unittest.TestCase):
    def test_selects_host_tools_in_paths_with_spaces(self):
        with tempfile.TemporaryDirectory(prefix="eor ndk ") as temporary:
            ndk = Path(temporary)
            for system, host, suffix in [("Linux", "linux-x86_64", ""),
                                         ("Darwin", "darwin-x86_64", ""),
                                         ("Windows", "windows-x86_64", ".exe")]:
                directory = ndk / "toolchains/llvm/prebuilt" / host / "bin"
                directory.mkdir(parents=True)
                for name in ("clang", "llvm-ar"):
                    (directory / (name + suffix)).touch()
            for system, host, suffix in [("Linux", "linux-x86_64", ""),
                                         ("Darwin", "darwin-x86_64", ""),
                                         ("Windows", "windows-x86_64", ".exe")]:
                with self.subTest(system=system):
                    clang, ar = ndk_tools(ndk, system)
                    self.assertEqual(host, clang.parents[1].name)
                    self.assertEqual("clang" + suffix, clang.name)
                    self.assertEqual("llvm-ar" + suffix, ar.name)

    def test_rejects_wrong_host_ndk(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(ValueError, "windows-x86_64"):
                ndk_tools(Path(temporary), "Windows")
            with self.assertRaisesRegex(ValueError, "Unsupported NDK build host"):
                ndk_tools(Path(temporary), "Unknown")

    def test_both_android_abis_get_explicit_linker_and_compiler_targets(self):
        for target, android_target in [("aarch64-linux-android", "aarch64-linux-android23"),
                                       ("armv7-linux-androideabi", "armv7a-linux-androideabi23")]:
            with self.subTest(target=target):
                original = {"RUSTFLAGS": "-C debuginfo=1"}
                env = compiler_environment(original, target, android_target,
                                           "C:/Android SDK/bin/clang.exe", "C:/Android SDK/bin/llvm-ar.exe")
                key = target.replace("-", "_")
                self.assertEqual("C:/Android SDK/bin/clang.exe", env["CARGO_TARGET_" + key.upper() + "_LINKER"])
                self.assertEqual("C:/Android SDK/bin/clang.exe", env["CC_" + key])
                self.assertEqual("C:/Android SDK/bin/llvm-ar.exe", env["AR_" + key])
                self.assertEqual("--target=" + android_target, env["CFLAGS_" + key])
                self.assertEqual(["-C", "debuginfo=1", "-C", "link-arg=--target=" + android_target],
                                 env["CARGO_ENCODED_RUSTFLAGS"].split("\x1f"))
                self.assertEqual({"RUSTFLAGS": "-C debuginfo=1"}, original)

    def test_preserves_encoded_flags_with_spaces_and_target_cflags(self):
        flags = "-L\x1fC:/native libs"
        env = compiler_environment({"CARGO_ENCODED_RUSTFLAGS": flags, "RUSTFLAGS": "ignored",
                                    "CFLAGS_aarch64_linux_android": "-O2"},
                                   "aarch64-linux-android", "aarch64-linux-android23", "clang", "ar")
        self.assertTrue(env["CARGO_ENCODED_RUSTFLAGS"].startswith(flags + "\x1f"))
        self.assertEqual("-O2 --target=aarch64-linux-android23", env["CFLAGS_aarch64_linux_android"])


if __name__ == "__main__":
    unittest.main()
