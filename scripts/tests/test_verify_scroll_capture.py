import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_scroll_capture import SCENARIOS, verify_capture


class ScrollCaptureTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        raw = self.root / 'raw'
        raw.mkdir()
        self.text = ''
        results = []
        for name in sorted(SCENARIOS):
            for status in (1, 0):
                self.text += ('INSTRUMENTATION_STATUS: class=com.example.macrobenchmark.SteadyScrollBenchmark\n'
                    f'INSTRUMENTATION_STATUS: test={name}\nINSTRUMENTATION_STATUS_CODE: {status}\n')
            outputs = []
            for index in range(10):
                filename = f'{name}-{index}.perfetto-trace'
                (raw / filename).write_bytes(b'capture-fixture-content')
                outputs.append({'type': 'PerfettoTrace', 'filename': filename})
            results.append({'className': 'com.example.macrobenchmark.SteadyScrollBenchmark',
                'name': name, 'repeatIterations': 10,
                'sampledMetrics': {'frameDurationCpuMs': {'runs': [[2., 3.]] * 10}},
                'profilerOutputs': outputs})
        self.text += 'OK (13 tests)\nINSTRUMENTATION_CODE: -1\n'
        self.payload = {'benchmarks': results}
        self.write()

    def write(self):
        (self.root / 'instrumentation.txt').write_text(self.text, encoding='utf-8')
        (self.root / 'raw/results.json').write_text(json.dumps(self.payload), encoding='utf-8')

    def test_complete_capture_is_still_not_performance_acceptance(self):
        result = verify_capture(self.root)
        self.assertTrue(result['captureStructureVerified'])
        self.assertFalse(result['performanceAccepted'])

    def test_ignored_test_cannot_hide_behind_ok_summary(self):
        self.text = self.text.replace('INSTRUMENTATION_STATUS_CODE: 0', 'INSTRUMENTATION_STATUS_CODE: -3', 1)
        self.write()
        with self.assertRaises(ValueError):
            verify_capture(self.root)

    def test_missing_result_and_duplicate_scenario_are_rejected(self):
        self.payload['benchmarks'][-1] = copy.deepcopy(self.payload['benchmarks'][0])
        self.write()
        with self.assertRaises(ValueError):
            verify_capture(self.root)

    def test_missing_trace_is_rejected(self):
        next((self.root / 'raw').glob('*.perfetto-trace')).unlink()
        with self.assertRaises(ValueError):
            verify_capture(self.root)

    def test_empty_frame_iteration_is_rejected(self):
        self.payload['benchmarks'][0]['sampledMetrics']['frameDurationCpuMs']['runs'][0] = []
        self.write()
        with self.assertRaises(ValueError):
            verify_capture(self.root)

    def test_nonfinite_cpu_sample_is_rejected(self):
        self.payload['benchmarks'][0]['sampledMetrics']['frameDurationCpuMs']['runs'][0] = [float('nan')]
        self.write()
        with self.assertRaises(ValueError):
            verify_capture(self.root)

    def test_old_output_without_test_execution_is_rejected(self):
        self.text = 'OK (13 tests)\nINSTRUMENTATION_CODE: -1\n'
        self.write()
        with self.assertRaises(ValueError):
            verify_capture(self.root)
