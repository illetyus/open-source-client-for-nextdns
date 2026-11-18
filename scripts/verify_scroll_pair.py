"""Check that two structured scroll captures can be compared.

This is a provenance and capture-integrity gate, not a smoothness verdict.
Presentation cadence, motion, thermal conditions and distributions still need
independent analysis of the trace files.
"""
import argparse
import json
import re
from pathlib import Path

try:
    from .verify_scroll_capture import verify_capture
except ImportError:  # Direct invocation: python scripts/verify_scroll_pair.py
    from verify_scroll_capture import verify_capture


def verify_pair(baseline, candidate):
    baseline, candidate = Path(baseline), Path(candidate)
    left = json.loads((baseline / 'metadata.json').read_text(encoding='utf-8-sig'))
    right = json.loads((candidate / 'metadata.json').read_text(encoding='utf-8-sig'))
    if (left.get('label'), right.get('label')) != ('baseline', 'candidate'):
        raise ValueError('Expected baseline and candidate captures in that order')
    for key in ('testSha256', 'model', 'os', 'expectedHz', 'compilation', 'diagnostics', 'iconWorkload'):
        if key not in left or key not in right or left[key] is None or left[key] != right[key]:
            raise ValueError(f'Capture metadata differs or is missing: {key}')
    if (type(left['expectedHz']) is not int or left['expectedHz'] not in (60, 120) or
            not isinstance(left['model'], str) or not re.fullmatch(r'SM-G78[01].*', left['model']) or
            not isinstance(left['os'], str) or not left['os'].strip() or
            left['compilation'] not in ('warmup', 'none', 'required') or
            type(left['diagnostics']) is not bool or
            left['iconWorkload'] not in ('unique', 'repeated', 'warm-repeated')):
        raise ValueError('Invalid device or workload metadata')
    for capture in (left, right):
        for key, pattern in (('testSha256', r'[0-9a-fA-F]{64}'),
                             ('appSha256', r'[0-9a-fA-F]{64}'),
                             ('sourceCommit', r'[0-9a-fA-F]{40}')):
            if not re.fullmatch(pattern, str(capture.get(key, ''))):
                raise ValueError(f'Invalid capture metadata: {key}')
        if capture.get('status') != 'RECORDED_REQUIRES_TRACE_REVIEW':
            raise ValueError('Capture has not completed its structural verification')
        if capture.get('performanceAccepted') is not False:
            raise ValueError('Unexpected acceptance claim in capture metadata')
    first, second = verify_capture(baseline), verify_capture(candidate)
    return {'pairStructureVerified': True, 'performanceAccepted': False,
            'baselineAppSha256': left['appSha256'], 'candidateAppSha256': right['appSha256'],
            'testSha256': left['testSha256'], 'expectedHz': left['expectedHz'],
            'baselineTraceCount': len(first['traceSha256']),
            'candidateTraceCount': len(second['traceSha256']),
            'scope': 'Same harness and declared conditions; not proof of actual cadence, thermal comparability or performance.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('baseline', type=Path)
    parser.add_argument('candidate', type=Path)
    args = parser.parse_args()
    print(json.dumps(verify_pair(args.baseline, args.candidate), indent=2))
