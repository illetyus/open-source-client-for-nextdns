"""Validate local capture structure; this does not certify frame cadence or smoothness."""
import json
import argparse
import hashlib
import math
import re
from pathlib import Path

SCENARIOS = frozenset(('setup', 'security', 'privacy', 'parental', 'denylist', 'allowlist',
    'analytics', 'analyticsMap', 'analyticsSeries', 'analyticsDevices', 'logs200', 'logs1000', 'settings'))


def verify_capture(root):
    root = Path(root)
    text = (root / 'instrumentation.txt').read_text(encoding='utf-8-sig')
    if 'OK (13 tests)' not in text or 'INSTRUMENTATION_CODE: -1' not in text:
        raise ValueError('Missing completed instrumentation summary')
    started, finished, status_fields = [], [], {}
    expected_class = 'com.example.macrobenchmark.SteadyScrollBenchmark'
    for line in text.splitlines():
        field = re.fullmatch(r'INSTRUMENTATION_STATUS: (class|test)=(.*)', line)
        if field:
            status_fields[field[1]] = field[2]
        status = re.fullmatch(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)', line)
        if status:
            code = int(status[1])
            if code not in (0, 1, 2):  # 2 is Benchmark's intermediate metric/status output.
                raise ValueError('Failed, skipped or unknown instrumentation status')
            if code in (0, 1):
                if status_fields.get('class') != expected_class:
                    raise ValueError('Unexpected instrumentation class')
                (started if code == 1 else finished).append(status_fields.get('test'))
            status_fields = {}
    if len(started) != len(SCENARIOS) or len(finished) != len(SCENARIOS) or \
            set(started) != SCENARIOS or set(finished) != SCENARIOS:
        raise ValueError('Every scenario must start and finish exactly once')

    results = []
    raw = root / 'raw'
    for path in raw.rglob('*.json'):
        value = json.loads(path.read_text(encoding='utf-8-sig'))
        if isinstance(value, dict) and 'benchmarks' in value:
            results.extend(value['benchmarks'])
    if len(results) != len(SCENARIOS) or {row.get('name') for row in results} != SCENARIOS:
        raise ValueError('Missing or duplicate benchmark result entries')

    trace_inventory = {}
    for path in raw.rglob('*.perfetto-trace'):
        if path.name in trace_inventory:
            raise ValueError('Ambiguous trace filename')
        trace_inventory[path.name] = path
    hashes = {}
    for result in results:
        if result.get('className') != expected_class or result.get('repeatIterations') != 10:
            raise ValueError('Unexpected benchmark class or iteration count')
        # BenchmarkData 1.5.0 SampledMetricResult: one nonempty frame array per iteration.
        runs = result.get('sampledMetrics', {}).get('frameDurationCpuMs', {}).get('runs', [])
        if len(runs) != 10 or any(not run or any(type(x) not in (int, float) or
                not math.isfinite(x) or x < 0 for x in run) for run in runs):
            raise ValueError('Missing, empty or invalid frame samples')
        outputs = [item for item in result.get('profilerOutputs', []) if item.get('type') == 'PerfettoTrace']
        if len(outputs) != 10:
            raise ValueError('Every measured iteration requires its Perfetto trace')
        for output in outputs:
            filename = Path(output['filename']).name
            path = trace_inventory.get(filename)
            if path is None or path.stat().st_size < 16 or filename in hashes:
                raise ValueError('Missing, empty or reused trace artifact')
            with path.open('rb') as trace:
                hashes[filename] = hashlib.file_digest(trace, 'sha256').hexdigest()
    return {'captureStructureVerified': True, 'performanceAccepted': False,
        'scenarios': len(results), 'iterationsPerScenario': 10, 'traceSha256': hashes,
        'scope': 'Structure and byte integrity only; actual cadence, frame deadlines, thermal and paired acceptance require trace analysis.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path)
    args = parser.parse_args()
    report = verify_capture(args.capture)
    (args.capture / 'capture-structure.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(f"Recorded {report['scenarios']} scenarios and {len(report['traceSha256'])} traces; performance is not yet accepted.")
