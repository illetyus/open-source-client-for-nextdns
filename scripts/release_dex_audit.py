"""Distinguish DEX type references from harmless library string constants.

DEX tables: https://source.android.com/docs/core/runtime/dex-format
APK signature/payload verification is separate; this reads standard DEX <= 040.
"""
import argparse
import json
import os
import re
import struct
import zipfile
from pathlib import Path


class TestCodeFound(ValueError):
    pass


def type_descriptors(data):
    if len(data) < 112 or data[:4] != b'dex\n' or data[4:8] not in (
        b'035\0', b'037\0', b'038\0', b'039\0', b'040\0'
    ):
        raise ValueError('Unsupported or truncated DEX header')
    def u32(offset):
        if offset < 0 or offset + 4 > len(data):
            raise ValueError('DEX offset outside file')
        return struct.unpack_from('<I', data, offset)[0]
    if u32(32) != len(data) or u32(36) != 112 or u32(40) != 0x12345678:
        raise ValueError('Invalid DEX size, header or endianness')
    strings, strings_off = u32(56), u32(60)
    types, types_off = u32(64), u32(68)
    if not strings or not types or types > 65535:
        raise ValueError('Missing or oversized DEX identifier tables')
    for count, offset in ((strings, strings_off), (types, types_off)):
        if offset < 112 or offset % 4 or offset + count * 4 > len(data):
            raise ValueError('DEX identifier table outside file')
    result = set()
    for index in range(types):
        string_index = u32(types_off + index * 4)
        if string_index >= strings:
            raise ValueError('DEX type has invalid string index')
        position = u32(strings_off + string_index * 4)
        if position < 112:
            raise ValueError('DEX string offset outside data')
        for _ in range(5):  # Skip the utf16_size ULEB128, not the string data.
            if position >= len(data):
                raise ValueError('Truncated DEX string length')
            byte = data[position]
            position += 1
            if byte < 128:
                break
        else:
            raise ValueError('Invalid DEX string length')
        end = data.find(b'\0', position)
        if end < 0:
            raise ValueError('Unterminated DEX type descriptor')
        result.add(data[position:end])
    return result


def mapped_descriptors(mapping):
    """Retain all original types, including R8 classes merged into a live class.

    A mapping is evidence of optimizer input, not a reason to allow a fixture.
    Inspect originals even when their code was inlined and their descriptor died.
    """
    originals = set()
    for line in mapping.splitlines():
        if not line or line.startswith('#') or line[0].isspace():
            continue
        header = re.fullmatch(r'([^\s:]+) -> ([^\s:]+):', line)
        if not header:
            raise ValueError('Malformed R8 class mapping')
        originals.add(('L' + header[1].replace('.', '/') + ';').encode('utf-8'))
    if mapping.strip() and not originals:
        raise ValueError('R8 mapping contains no class headers')
    return originals


def audit_dex(data, mapping=None):
    descriptors = type_descriptors(data)
    original_count = len(descriptors)
    if mapping is not None:
        descriptors |= mapped_descriptors(mapping)
    prefixes = (b'Lorg/robolectric/', b'Lokhttp3/mockwebserver/', b'Lorg/jacoco/',
                b'Lcom/example/ReleaseSecurityRuntimeTest')
    retired_product_type_prefixes = (
        b'Lcom/example/data/diagnostics/LibreSpeedClient',
        b'Lcom/example/ui/screens/SpeedTestDialog',
        b'Lcom/example/BenchmarkFixtureActivity',
    )
    for descriptor in sorted(descriptors):
        if descriptor.lstrip(b'[').startswith(prefixes):
            raise TestCodeFound('Forbidden test type: ' + descriptor.decode('ascii', errors='replace'))
        if descriptor.lstrip(b'[').startswith(retired_product_type_prefixes):
            reason = 'Benchmark fixture type' if descriptor.startswith(b'Lcom/example/BenchmarkFixtureActivity') else 'Retired product type'
            raise TestCodeFound(reason + ': ' + descriptor.decode('ascii'))
    for marker in (b'$jacoco', b'RELEASE_AUDIT_SYNTHETIC_CANARY_20261009',
                   b'garbage.php', b'empty.php'):
        if marker in data:
            raise TestCodeFound('Forbidden release marker: ' + marker.decode('ascii'))
    return original_count


def apk_dex(path):
    with zipfile.ZipFile(path) as archive:
        names = [name for name in archive.namelist() if re.fullmatch(r'classes\d*\.dex', name)]
        if not names or len(names) != len(set(names)):
            raise ValueError('Missing or duplicate APK DEX entries')
        return [(name, archive.read(name)) for name in names]


if __name__ == '__main__':
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument('--release-apk', type=Path, default=root/'app/build/outputs/apk/release/app-release-unsigned.apk')
    parser.add_argument('--test-apk', type=Path)
    parser.add_argument('--mapping', type=Path, help='Matching R8 mapping.txt; mandatory for a minified payload')
    parser.add_argument('--output', type=Path, default=root/'build/compliance/release-dex-audit.json')
    args = parser.parse_args()
    release = apk_dex(args.release_apk)
    mapping = args.mapping.read_text(encoding='utf-8') if args.mapping else None
    if args.mapping and not mapping.strip():
        raise ValueError('Empty R8 mapping')
    count = sum(audit_dex(data, mapping=mapping) for _, data in release)
    fixture = [args.test_apk] if args.test_apk else list((root/'app/build/outputs/apk/androidTest/debug').glob('*.apk'))
    assert len(fixture) == 1
    rejected = []
    for name, data in apk_dex(fixture[0]):
        try:
            audit_dex(data)
        except TestCodeFound as error:
            rejected.append({'dex': name, 'reason': str(error)})
    assert rejected, 'Real test APK must be rejected, not classified as production'
    for broken in (release[0][1][:16], release[0][1][:112]):
        try:
            audit_dex(broken)
        except ValueError:
            pass
        else:
            raise AssertionError('Malformed DEX must fail closed')
    out = args.output
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({'commit': os.environ.get('GITHUB_SHA', 'local'),
        'releaseDexFiles': len(release), 'typeReferencesExamined': count,
        'testFixtureRejected': rejected, 'malformedDexRejected': True,
        'r8MappingExamined': args.mapping is not None,
        'scope': 'Test/coverage type references and synthetic canary; ordinary production-library strings are allowed.'}, indent=2)+'\n')
    print(f'PASS: {len(release)} release DEX files / {count} type references; real test APK and malformed DEX rejected')
