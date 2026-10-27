#!/usr/bin/env python3
"""Cross-compile the on-device banner renderer; invoked by Gradle, never by the app."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.request
import sys
from banner_build_support import compiler_environment, ndk_tools, rust_tool

if sys.version_info < (3, 12):
    raise SystemExit('Python 3.12+ is required. See app/src/main/rust/three_ds_banner/README.md')

ARCHIVES = {
    "cgfx-parser": "ed3eb09f404aee5868d1bd4f07b27492fc6f07f1a484bf343901a1d1f4b341e8",
    "cgfx-renderer": "4ec5cdc89b1cfee9ef829a82b6cea4ff4c6a85a0c5911edc0deedc303e18ffb8",
    "cgfx-wgpu": "14539016263327552d3ce42381f4da7ab55ef5f85ea495a70f645843501824f2",
}


def prepare_vendor(work):
    vendor = work / "vendor"
    vendor.mkdir(exist_ok=True)
    for name, digest in ARCHIVES.items():
        archive = vendor / f"{name}-0.1.0.crate"
        if not archive.exists():
            urllib.request.urlretrieve(f"https://static.crates.io/crates/{name}/{name}-0.1.0.crate", archive)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != digest:
            raise ValueError(f"Checksum mismatch: {archive}")
        with tarfile.open(archive) as contents:
            contents.extractall(vendor, filter="data")
    shader = vendor / "cgfx-wgpu-0.1.0/src/shader.wgsl"
    text = shader.read_text(encoding='utf-8')
    original = "output.color = input.color * draw.diffuse;"
    if text.count(original) != 1:
        raise ValueError("Banner shader correction no longer matches the pinned renderer")
    shader.write_text(text.replace(original, "output.color = input.color;"), encoding='utf-8')


root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--ndk', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
cargo = os.environ.get('EOR_CARGO') or rust_tool('cargo')
if not Path(cargo).is_file():
    raise SystemExit('Rust/Cargo is required. Run: python3 tools/setup_3ds_banner.py')
try:
    clang, ar = ndk_tools(args.ndk)
except ValueError as error:
    raise SystemExit(str(error)) from error
source = root / 'app/src/main/rust/three_ds_banner'
work = root / 'app/build/banner-renderer'
work.mkdir(parents=True, exist_ok=True)
shutil.copyfile(root / 'rust-toolchain.toml', work / 'rust-toolchain.toml')
for name in ['Cargo.toml', 'Cargo.lock']:
    if (source / name).exists():
        shutil.copyfile(source / name, work / name)
shutil.copytree(source / 'src', work / 'src', dirs_exist_ok=True)
prepare_vendor(work)
for abi, target, android_target in [('arm64-v8a', 'aarch64-linux-android', 'aarch64-linux-android23'),
                                    ('armeabi-v7a', 'armv7-linux-androideabi', 'armv7a-linux-androideabi23')]:
    env = compiler_environment(os.environ, target, android_target, clang, ar)
    subprocess.run([cargo, 'build', '--release', '--locked', '--target', target,
                    '--manifest-path', str(work / 'Cargo.toml')], cwd=work, env=env, check=True)
    target_dir = Path(env.get('CARGO_TARGET_DIR', work / 'target'))
    if not target_dir.is_absolute():
        target_dir = work / target_dir
    output = args.output / abi
    output.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(target_dir / target / 'release/libeor_banner.so', output / 'libeor_banner.so')

# Ship the dependency notices with the APK rather than leaving native code unattributed.
metadata = json.loads(subprocess.check_output([cargo, 'metadata', '--locked', '--format-version', '1',
    '--filter-platform', 'aarch64-linux-android', '--manifest-path', str(work / 'Cargo.toml')], cwd=work, encoding='utf-8'))
notices = ['eOr 3DS banner renderer — third-party source notices\n',
    'The cgfx crates are used under Apache-2.0; the local vertex-color shader correction is applied at build time.\n']
for package in sorted(metadata['packages'], key=lambda p: p['name']):
    notices.append(f"\n=== {package['name']} {package['version']} — {package.get('license') or 'see source'} ===\n")
    notices.append(f"Source: {package.get('repository') or 'https://crates.io/crates/' + package['name']}\n")
    directory = Path(package['manifest_path']).parent
    for file in sorted(directory.iterdir()):
        if file.is_file() and file.name.upper().startswith(('LICENSE', 'COPYING', 'NOTICE')):
            notices.append(file.read_text(encoding='utf-8', errors='replace') + '\n')
notice_dir = args.output.parent / 'banner-licenses'
notice_dir.mkdir(parents=True, exist_ok=True)
(notice_dir / '3ds-banner-licenses.txt').write_text(''.join(notices), encoding='utf-8')
