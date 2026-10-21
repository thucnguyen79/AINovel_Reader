"""Downloads the prebuilt sherpa-onnx Android AAR into third_party/m2 (local Maven layout).

Usage: python tools/sherpa/fetch_sherpa_aar.py [version]
The version must match `sherpaOnnx` in gradle/libs.versions.toml. The AAR (~50 MB, Apache-2.0) is
git-ignored; run this once after cloning, then build as usual.
"""
import os
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def catalog_version() -> str:
    with open(os.path.join(ROOT, 'gradle', 'libs.versions.toml'), encoding='utf-8') as f:
        for line in f:
            if line.startswith('sherpaOnnx'):
                return line.split('=', 1)[1].strip().strip('"')
    raise SystemExit('sherpaOnnx version not found in gradle/libs.versions.toml')


def main() -> None:
    version = sys.argv[1] if len(sys.argv) > 1 else catalog_version()
    target_dir = os.path.join(ROOT, 'third_party', 'm2', 'com', 'k2fsa', 'sherpa', 'onnx', 'sherpa-onnx-android', version)
    os.makedirs(target_dir, exist_ok=True)
    target = os.path.join(target_dir, f'sherpa-onnx-android-{version}.aar')
    if os.path.isfile(target) and os.path.getsize(target) > 1_000_000:
        print('already present:', target)
        return
    url = f'https://github.com/k2-fsa/sherpa-onnx/releases/download/v{version}/sherpa-onnx-{version}.aar'
    print('downloading', url)
    tmp = target + '.part'
    with urllib.request.urlopen(url) as resp, open(tmp, 'wb') as out:
        total = int(resp.headers.get('Content-Length') or 0)
        done = 0
        while True:
            chunk = resp.read(1 << 20)
            if not chunk:
                break
            out.write(chunk)
            done += len(chunk)
            if total:
                print(f'\r{done * 100 // total}%', end='', flush=True)
    os.replace(tmp, target)
    print('\nsaved', target)
    pom = os.path.join(target_dir, f'sherpa-onnx-android-{version}.pom')
    if not os.path.isfile(pom):
        with open(pom, 'w', encoding='utf-8') as f:
            f.write(
                '<?xml version="1.0" encoding="UTF-8"?>\n<project xmlns="http://maven.apache.org/POM/4.0.0">\n'
                '  <modelVersion>4.0.0</modelVersion>\n  <groupId>com.k2fsa.sherpa.onnx</groupId>\n'
                f'  <artifactId>sherpa-onnx-android</artifactId>\n  <version>{version}</version>\n  <packaging>aar</packaging>\n</project>\n'
            )
        print('wrote', pom)


if __name__ == '__main__':
    main()
