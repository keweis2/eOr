"""Contributor bootstrap checks: path portability and installer side effects."""
import contextlib
import io
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import setup as bootstrap


class ContributorSetupTest(unittest.TestCase):
    def test_local_config_preserves_credentials_and_roundtrips_windows_unicode_paths(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / "local.properties"
            secrets = "# private settings\r\nSS_DEV_ID=private\\ value\r\nSS_DEV_PASSWORD=a=b:c\\\\d\r\n"
            path.write_bytes((secrets + "sdk.dir=old\\\r\n path\r\neor.python=old\r\n").encode())
            sdk, python = "C:\\Android SDK\\José\\😀", "C:\\Python 3.12\\python.exe"
            bootstrap.write_local_properties(root, sdk, python)
            self.assertTrue(path.read_bytes().startswith(secrets.encode()))
            self.assertEqual(sdk, bootstrap.read_property(path, "sdk.dir"))
            self.assertEqual(python, bootstrap.read_property(path, "eor.python"))
            before = path.stat().st_mtime_ns
            bootstrap.write_local_properties(root, sdk, python)
            self.assertEqual(before, path.stat().st_mtime_ns)

    def test_properties_comments_are_not_continuations(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "local.properties"
            path.write_text("# comment\\\nsdk.dir /Android\\ SDK\nsdk.dir=/final\n")
            self.assertEqual("/final", bootstrap.read_property(path, "sdk.dir"))
            path.write_text("! comment\\\nsdk.dir /Android\\ SDK\n")
            self.assertEqual("/Android SDK", bootstrap.read_property(path, "sdk.dir"))

    def test_explicit_sdk_then_local_config_take_precedence_over_environment(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {"ANDROID_HOME": "/env-sdk"}):
            root = Path(temporary)
            bootstrap.write_local_properties(root, str(root / "local sdk"), sys.executable)
            self.assertEqual((root / "local sdk").resolve(), bootstrap.find_sdk(root))
            self.assertEqual((root / "override").resolve(), bootstrap.find_sdk(root, root / "override"))

    def test_old_and_unsupported_java_are_rejected_and_studio_jdk_can_launch(self):
        for output, expected in [('java version "1.8.0_402"', 8),
                                 ('openjdk version "21.0.11"', 21),
                                 ('openjdk 25.0.5 2026-10-20', 25), ("unknown", 0)]:
            self.assertEqual(expected, bootstrap.java_major(output))
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            java = root / "bin" / ("java.exe" if os.name == "nt" else "java")
            java.parent.mkdir()
            java.touch()
            with patch.object(bootstrap.subprocess, "run") as run:
                run.return_value.returncode = 0
                run.return_value.stdout = ""
                for major in (8, 21, 28):
                    run.return_value.stderr = f'openjdk version "{major}"'
                    self.assertEqual(root.resolve() if major == 21 else None,
                                     bootstrap.find_java(root, root))

    def make_sdk(self, root):
        sdk = root / "Android SDK"
        sdk.mkdir()
        for name in bootstrap.required_packages(bootstrap.ROOT):
            path = sdk.joinpath(*name.split(";"))
            path.mkdir(parents=True, exist_ok=True)
            (path / "package.xml").touch()
        return sdk

    def test_check_never_writes_config_installs_packages_or_launches_gradle(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            packages = bootstrap.required_packages(bootstrap.ROOT)
            sdk = self.make_sdk(root)
            with patch.object(bootstrap, "ROOT", root), \
                    patch.object(bootstrap, "required_packages", return_value=packages), \
                    patch.object(bootstrap, "find_sdk", return_value=sdk), \
                    patch.object(bootstrap, "find_java", return_value=root), \
                    patch.object(bootstrap, "host_problem", return_value=None), \
                    patch.object(bootstrap, "write_local_properties") as write, \
                    patch.object(bootstrap.subprocess, "run") as run, \
                    contextlib.redirect_stdout(io.StringIO()):
                bootstrap.main(["--check"])
                write.assert_not_called()
                self.assertEqual(1, run.call_count)
                self.assertIn("--check", run.call_args.args[0])
                self.assertIn("--ndk", run.call_args.args[0])
                (sdk / "ndk" / packages[3].split(";")[1] / "package.xml").unlink()
                run.reset_mock()
                with self.assertRaisesRegex(ValueError, "Missing SDK packages"):
                    bootstrap.main(["--check"])
                run.assert_not_called()

    def test_installer_only_requests_missing_packages_and_passes_paths_as_arguments(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            packages = bootstrap.required_packages(bootstrap.ROOT)
            sdk = self.make_sdk(root)
            manager = sdk / "tools with spaces/sdkmanager"
            with patch.object(bootstrap, "ROOT", root), \
                    patch.object(bootstrap, "required_packages", return_value=packages), \
                    patch.object(bootstrap, "find_sdk", return_value=sdk), \
                    patch.object(bootstrap, "find_java", return_value=root), \
                    patch.object(bootstrap, "host_problem", return_value=None), \
                    patch.object(bootstrap, "find_sdkmanager", return_value=manager), \
                    patch.object(bootstrap, "missing_packages", side_effect=[[packages[3]], []]), \
                    patch.object(bootstrap.subprocess, "run") as run, \
                    contextlib.redirect_stdout(io.StringIO()):
                bootstrap.main(["--build"])
                install, rust, gradle = run.call_args_list
                self.assertEqual([str(manager), f"--sdk_root={sdk}", "--install", packages[3]], install.args[0])
                self.assertNotIn("input", install.kwargs)  # License prompts retain stdin.
                self.assertEqual(str(root), install.kwargs["env"]["JAVA_HOME"])
                self.assertIn(":app:assembleFullDebug", gradle.args[0])
                self.assertEqual(str(sdk), bootstrap.read_property(root / "local.properties", "sdk.dir"))
                self.assertEqual(sys.executable, bootstrap.read_property(root / "local.properties", "eor.python"))

    def test_host_checks_explain_missing_macos_tools_and_unsupported_linux_ndk_host(self):
        with patch.object(bootstrap.platform, "system", return_value="Darwin"), \
                patch.object(bootstrap.subprocess, "run") as run:
            run.return_value.returncode = 1
            self.assertIn("xcode-select --install", bootstrap.host_problem())
        with patch.object(bootstrap.platform, "system", return_value="Linux"), \
                patch.object(bootstrap.platform, "machine", return_value="aarch64"):
            self.assertIn("x64", bootstrap.host_problem())

    def test_windows_arm_host_needs_matching_msvc_and_windows_sdk(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            vswhere = root / "Microsoft Visual Studio/Installer/vswhere.exe"
            vswhere.parent.mkdir(parents=True)
            vswhere.touch()
            sdk = root / "Windows Kits/10/Include"
            sdk.mkdir(parents=True)
            with patch.dict(os.environ, {"ProgramFiles(x86)": str(root)}), \
                    patch.object(bootstrap.platform, "system", return_value="Windows"), \
                    patch.object(bootstrap.platform, "machine", return_value="ARM64"), \
                    patch.object(bootstrap.subprocess, "run") as run:
                run.return_value.returncode = 0
                run.return_value.stdout = str(root / "Visual Studio")
                self.assertIsNone(bootstrap.host_problem())
                self.assertIn("Microsoft.VisualStudio.Component.VC.Tools.ARM64", run.call_args.args[0])
                sdk.rmdir()
                self.assertIn("Windows SDK", bootstrap.host_problem())


if __name__ == "__main__":
    unittest.main()
