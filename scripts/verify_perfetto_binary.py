"""Preflight the packaged Perfetto binary against app-side checksum constants.

Checks exact SHA-256 constants in DEX; native handshake remains the runtime gate.
Does not disable, replace or bypass Perfetto's on-device verification.
"""
import argparse
import hashlib
import zipfile


def verify(app, measurement):
    with zipfile.ZipFile(app) as archive:
        dex = b'\0'.join(archive.read(name) for name in archive.namelist()
                         if name.startswith('classes') and name.endswith('.dex'))
    if not dex:
        raise ValueError('Application contains no DEX')
    checked = {}
    with zipfile.ZipFile(measurement) as archive:
        for name in archive.namelist():
            if name.startswith('lib/') and name.endswith('/libtracing_perfetto.so'):
                checksum = hashlib.sha256(archive.read(name)).hexdigest()
                # A 64-character ASCII DEX string starts with ULEB length 0x40.
                if b'\x40' + checksum.encode('ascii') + b'\0' not in dex:
                    raise ValueError('App Perfetto metadata does not approve ' + name + ': ' + checksum)
                checked[name] = checksum
    if not checked:
        raise ValueError('Measurement APK contains no Perfetto native binaries')
    return checked


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('app')
    parser.add_argument('measurement')
    args = parser.parse_args()
    for name, checksum in verify(args.app, args.measurement).items():
        print(name, checksum)
