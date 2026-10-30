import hashlib
import io
import unittest
import zipfile

from scripts.verify_perfetto_binary import verify


class PerfettoBinaryTest(unittest.TestCase):
    def fixture(self, dex, binary=b'published-native'):
        app, test = io.BytesIO(), io.BytesIO()
        with zipfile.ZipFile(app, 'w') as z:
            if dex is not None:
                z.writestr('classes.dex', dex)
        with zipfile.ZipFile(test, 'w') as z:
            if binary is not None:
                z.writestr('lib/arm64-v8a/libtracing_perfetto.so', binary)
        return app, test

    def test_matching_digest(self):
        digest = hashlib.sha256(b'published-native').hexdigest()
        self.assertEqual(list(verify(*self.fixture(b'header\x40' + digest.encode() + b'\0')).values()), [digest])

    def test_different_version_rejected(self):
        with self.assertRaisesRegex(ValueError, 'does not approve'):
            verify(*self.fixture(hashlib.sha256(b'old-version').hexdigest().encode() + b'\0'))

    def test_missing_dex_rejected(self):
        with self.assertRaisesRegex(ValueError, 'no DEX'):
            verify(*self.fixture(None))

    def test_missing_binary_rejected(self):
        with self.assertRaisesRegex(ValueError, 'no Perfetto'):
            verify(*self.fixture(b'dex', None))

    def test_unterminated_digest_rejected(self):
        with self.assertRaisesRegex(ValueError, 'does not approve'):
            verify(*self.fixture(hashlib.sha256(b'published-native').hexdigest().encode() + b'1\0'))
