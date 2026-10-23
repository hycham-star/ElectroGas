#!/usr/bin/env python3
"""Fetch pinned official Android tools with published SHA-1 verification."""
import hashlib
import os
from pathlib import Path
import subprocess
import zipfile

root = Path(os.environ.get('ELECTROGAS_TOOLS', '/workspace/android-tools'))
root.mkdir(parents=True, exist_ok=True)
artifacts = [
    ('https://dl.google.com/android/repository/platform-35_r02.zip', 'platform.zip', '0bb560a90a7a2cbd0dd8348224d518b638fe7949', 'platform'),
    ('https://dl.google.com/android/repository/build-tools_r35_linux.zip', 'build-tools.zip', '2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc', 'build-tools'),
    ('https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.39.0/ecj-3.39.0.jar', 'ecj.jar', 'da787c5669578ffd5324bf29c18b361904a5a1bb', None),
]
for url, name, checksum, destination in artifacts:
    archive = root / name
    if not archive.exists():
        subprocess.run(['curl', '-fL', '--retry', '2', url, '-o', str(archive)], check=True)
    if hashlib.sha1(archive.read_bytes()).hexdigest() != checksum:
        raise RuntimeError(f'Checksum mismatch: {archive}; remove the failed download and retry')
    if destination:
        with zipfile.ZipFile(archive) as z:
            z.extractall(root / destination)
for tool in ('aapt2', 'zipalign'):
    (root / 'build-tools/android-15' / tool).chmod(0o755)
print('Android platform 35, build tools 35.0.0 and ECJ 3.39.0 verified.')
