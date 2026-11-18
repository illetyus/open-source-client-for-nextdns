import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import verify_scroll_pair


class ScrollPairTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.left, self.right = self.root / 'baseline', self.root / 'candidate'
        self.left.mkdir()
        self.right.mkdir()
        self.metadata = {'testSha256': 'a' * 64, 'appSha256': 'b' * 64,
            'sourceCommit': 'c' * 40, 'model': 'SM-G780G', 'os': '16',
            'expectedHz': 120, 'compilation': 'warmup', 'diagnostics': False,
            'iconWorkload': 'unique', 'status': 'RECORDED_REQUIRES_TRACE_REVIEW',
            'performanceAccepted': False}
        self.write(self.left, label='baseline')
        self.write(self.right, label='candidate')
        mock = patch.object(verify_scroll_pair, 'verify_capture', return_value={'traceSha256': {'a': 'b'}})
        self.addCleanup(mock.stop)
        mock.start()

    def write(self, root, **changes):
        (root / 'metadata.json').write_text(json.dumps(self.metadata | changes), encoding='utf-8')

    def test_matching_pair_does_not_claim_performance(self):
        result = verify_scroll_pair.verify_pair(self.left, self.right)
        self.assertTrue(result['pairStructureVerified'])
        self.assertFalse(result['performanceAccepted'])

    def test_different_measurement_apk_is_rejected(self):
        self.write(self.right, label='candidate', testSha256='d' * 64)
        with self.assertRaisesRegex(ValueError, 'testSha256'):
            verify_scroll_pair.verify_pair(self.left, self.right)

    def test_different_workloads_are_rejected(self):
        self.write(self.right, label='candidate', iconWorkload='warm-repeated')
        with self.assertRaisesRegex(ValueError, 'iconWorkload'):
            verify_scroll_pair.verify_pair(self.left, self.right)

    def test_matching_but_invalid_device_and_refresh_are_rejected(self):
        self.write(self.left, label='baseline', expectedHz=999)
        self.write(self.right, label='candidate', expectedHz=999)
        with self.assertRaisesRegex(ValueError, 'Invalid device'):
            verify_scroll_pair.verify_pair(self.left, self.right)
        self.write(self.left, label='baseline', model='emulator')
        self.write(self.right, label='candidate', model='emulator')
        with self.assertRaisesRegex(ValueError, 'Invalid device'):
            verify_scroll_pair.verify_pair(self.left, self.right)

    def test_unverified_or_already_accepted_capture_is_rejected(self):
        self.write(self.right, label='candidate', status='COLLECTING')
        with self.assertRaisesRegex(ValueError, 'structural verification'):
            verify_scroll_pair.verify_pair(self.left, self.right)
        self.write(self.right, label='candidate', performanceAccepted=True)
        with self.assertRaisesRegex(ValueError, 'acceptance claim'):
            verify_scroll_pair.verify_pair(self.left, self.right)
