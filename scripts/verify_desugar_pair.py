"""Check desugared type assignability with instrumentation's test-first loader.

This preflight catches incompatible duplicate j$ hierarchies, not arbitrary
bytecode or member ABI errors. Native runtime tests remain the acceptance gate.
DEX class_def/type_list layout: https://source.android.com/docs/core/runtime/dex-format
"""
import argparse
import struct
import zipfile

from release_dex_audit import type_descriptors


def dex_hierarchy(data):
    type_descriptors(data)  # Validate standard DEX header and identifier bounds.

    def u32(offset):
        if offset < 0 or offset + 4 > len(data):
            raise ValueError("DEX hierarchy offset outside file")
        return struct.unpack_from("<I", data, offset)[0]

    strings = []
    for index in range(u32(56)):
        offset = u32(u32(60) + index * 4)
        for _ in range(5):
            if offset >= len(data):
                raise ValueError("Truncated DEX string")
            byte = data[offset]
            offset += 1
            if byte < 128:
                break
        else:
            raise ValueError("Invalid DEX string length")
        end = data.find(b"\0", offset)
        if end < 0:
            raise ValueError("Unterminated DEX string")
        # Preserve non-ASCII Java identifiers, including modified UTF-8 surrogates.
        strings.append(data[offset:end])
    types = [strings[u32(u32(68) + index * 4)].decode("utf-8", errors="surrogatepass")
             for index in range(u32(64))]

    def type_at(index):
        if index >= len(types):
            raise ValueError("Invalid DEX hierarchy type index")
        return types[index]

    count, start = u32(96), u32(100)
    if start % 4 or (count and start < 112) or start + count * 32 > len(data):
        raise ValueError("Invalid DEX class table")
    result = {}
    for index in range(count):
        offset = start + index * 32
        name = type_at(u32(offset))
        parent = u32(offset + 8)
        parents = [] if parent == 0xffffffff else [type_at(parent)]
        interface_offset = u32(offset + 12)
        if interface_offset:
            size = u32(interface_offset)
            if interface_offset % 4 or interface_offset < 112 or interface_offset + 4 + size * 2 > len(data):
                raise ValueError("Invalid DEX interface list")
            parents.extend(type_at(struct.unpack_from("<H", data, interface_offset + 4 + i * 2)[0])
                           for i in range(size))
        if name in result:
            raise ValueError("Duplicate DEX class definition")
        result[name] = tuple(parents)
    return result


def apk_hierarchy(path):
    result = {}
    with zipfile.ZipFile(path) as apk:
        for name in apk.namelist():
            if not name.endswith(".dex"):
                continue
            classes = dex_hierarchy(apk.read(name))
            if result.keys() & classes.keys():
                raise ValueError("Duplicate APK class definition")
            result.update(classes)
    if not result:
        raise ValueError("APK has no DEX class definitions")
    return result


def ancestors(name, hierarchy, active=None):
    active = set() if active is None else active
    if name in active:
        raise ValueError("DEX inheritance cycle")
    active.add(name)
    result = set()
    for parent in hierarchy.get(name, ()):
        result.add(parent)
        result.update(ancestors(parent, hierarchy, active))
    active.remove(name)
    return result


def verify_hierarchy(app, test):
    effective = app | test
    for name in sorted(app):
        if not name.startswith("Lj$/"):
            continue
        missing = ancestors(name, app) - ancestors(name, effective)
        if missing:
            raise ValueError(f"Test APK breaks app assignability: {name} lost {sorted(missing)}")
    return len({name for name in app.keys() & test.keys() if name.startswith("Lj$/")})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("app_apk")
    parser.add_argument("test_apk")
    args = parser.parse_args()
    count = verify_hierarchy(apk_hierarchy(args.app_apk), apk_hierarchy(args.test_apk))
    print(f"Desugared type hierarchy compatible: {count} shared classes; native tests still required")


if __name__ == "__main__":
    main()
