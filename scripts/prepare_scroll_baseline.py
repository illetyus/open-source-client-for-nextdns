"""Export pre-optimization sources with exactly the current measurement harness.

Does not switch the working branch or copy candidate optimizations into the baseline.
The immutable Git archive and explicit measurement-only overlays form its provenance.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path
import subprocess
import zipfile


def digest(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def prepare(root, destination, baseline_ref):
    root, destination = root.resolve(), destination.resolve()
    if destination.exists():
        raise ValueError('Use a new baseline directory; existing evidence is never overwritten')
    if root not in destination.parents:
        raise ValueError('Baseline export must remain inside this workspace')
    commit = subprocess.check_output(['git', '-C', str(root), 'rev-parse', '--verify',
        baseline_ref + '^{commit}'], text=True).strip()
    destination.mkdir(parents=True)
    archive = destination / 'original-source.zip'
    subprocess.run(['git', '-C', str(root), 'archive', '--format=zip', '--output', str(archive), commit], check=True)
    source = destination / 'source'
    source.mkdir()
    with zipfile.ZipFile(archive) as files:
        for entry in files.infolist():
            target = (source / entry.filename).resolve()
            if source not in target.parents and target != source:
                raise ValueError('Archive path escaped the baseline source folder')
        files.extractall(source)

    harness = tuple(str(path.relative_to(root)).replace('\\', '/')
        for directory in ('macrobenchmark/src/main', 'app/src/benchmark/java')
        for path in sorted((root / directory).rglob('*.kt')))
    for relative in harness:
        (source / relative).parent.mkdir(parents=True, exist_ok=True)
        (source / relative).write_bytes((root / relative).read_bytes())

    # Diagnostic-only dependency, identical on both sides; never copy candidate
    # production build settings or main implementation into the baseline.
    build = source / 'app/build.gradle.kts'
    dependency = 'androidx.metrics:metrics-performance:1.0.0'
    # Both sides must use the same client/checksum metadata as the measurement
    # APK's Perfetto binary. This overlay only changes the benchmark variant.
    catalog = (root / 'gradle/libs.versions.toml').read_text(encoding='utf-8')
    perfetto = re.search(r'^tracingPerfetto = "([0-9.]+)"$', catalog, re.MULTILINE)
    if perfetto is None:
        raise ValueError('Missing explicit Perfetto version for the paired harness')
    build.write_text(build.read_text(encoding='utf-8') +
        '\ndependencies { add("benchmarkImplementation", "' + dependency + '")\n' +
        'add("benchmarkImplementation", "androidx.tracing:tracing-perfetto:' +
        perfetto[1] + '") }\n',
        encoding='utf-8', newline='\n')

    # The exported commit already contains the strict, injected favicon provider.
    # Verify it, rather than accidentally copying the candidate's shared-flight loader.
    favicon = source / 'app/src/main/java/com/example/ui/screens/FaviconImage.kt'
    original_favicon = favicon.read_text(encoding='utf-8')
    if 'LocalNextDnsFaviconLoader' not in original_favicon or 'private val flights' in original_favicon:
        raise ValueError('Expected the unoptimized instrumented baseline source')

    relative = 'app/src/main/java/com/example/ui/screens/AnalyticsScreen.kt'
    screen = source / relative
    before = screen.read_text(encoding='utf-8')
    modifier = '.fillMaxSize()\n      .padding(horizontal = 16.dp)'
    card = 'NextDnsCard(title = AppStrings.get(R.string.ui_238a17ed89), modifier = modifier)'
    if before.count(modifier) != 1 or before.count(card) != 1:
        raise ValueError('Baseline analytics source changed; do not guess at the overlay')
    ready = ('.fillMaxSize()\n'
        '      .testTag(if (!isAnalyticsLoading && analyticsLastSuccessAt != null &&\n'
        '        !analyticsSeries.loading && analyticsSeries.rows.isNotEmpty()) "analytics_ready" else "analytics_loading")\n'
        '      .padding(horizontal = 16.dp)')
    screen.write_text(before.replace(modifier, ready).replace(card,
        card[:-1] + '.testTag("analytics_devices_card"))'), encoding='utf-8', newline='\n')

    original_main = {}
    with zipfile.ZipFile(archive) as files:
        for entry in files.infolist():
            if entry.filename.startswith('app/src/main/') and not entry.is_dir():
                original_main[entry.filename] = hashlib.sha256(files.read(entry)).hexdigest()
    actual_main = {str(path.relative_to(source)).replace('\\', '/'): digest(path)
        for path in (source / 'app/src/main').rglob('*') if path.is_file()}
    changed = {name for name in original_main.keys() | actual_main.keys()
        if original_main.get(name) != actual_main.get(name)}
    if changed != {relative}:
        raise ValueError('Production baseline changed outside the measurement-only analytics markers')
    manifest = {'baselineSourceCommit': commit, 'sourceArchiveSha256': digest(archive),
        'measurementApkPolicy': 'Use one current-tree measurement APK for both apps and require identical testSha256 values; do not build measurement APKs independently from this archive.',
        'measurementHarnessSha256': {name: digest(source / name) for name in harness},
        'benchmarkDependencyOverlaySha256': digest(build),
        'mainMeasurementOnlyOverlay': {relative: digest(screen)},
        'originalMainSourceSha256': original_main,
        'scope': 'Unoptimized source plus two readiness/deep-target test tags and current synthetic measurement harness; no device acceptance.'}
    (destination / 'provenance.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    return source


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination', type=Path)
    parser.add_argument('--baseline-ref', default='ff8a0b6')
    args = parser.parse_args()
    print(prepare(Path(__file__).resolve().parents[1], args.destination, args.baseline_ref))
