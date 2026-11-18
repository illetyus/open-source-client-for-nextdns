import sys
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import verify_desugar_pair as audit


def dex_fixture():
    # One class implements an interface and extends Object. Write real DEX
    # table offsets so these tests exercise parsing, not a mocked decoder.
    names = [b"Lj$/A;", b"Ljava/lang/Object;", "Lj$/\u00c9;".encode()]
    data = bytearray(112 + 3 * 4 + 3 * 4 + 32)
    data[:8] = b"dex\n039\0"
    struct.pack_into("<II", data, 36, 112, 0x12345678)
    struct.pack_into("<II", data, 56, 3, 112)
    struct.pack_into("<II", data, 64, 3, 124)
    struct.pack_into("<II", data, 96, 1, 136)
    for i, name in enumerate(names):
        struct.pack_into("<I", data, 112 + i * 4, len(data))
        struct.pack_into("<I", data, 124 + i * 4, i)
        data.extend(bytes([len(name)]) + name + b"\0")
    while len(data) % 4:
        data.append(0)
    struct.pack_into("<IIII", data, 136, 0, 1, 1, len(data))
    data.extend(struct.pack("<IH", 1, 2))
    struct.pack_into("<I", data, 32, len(data))
    return bytes(data)


class DesugaredTypeShadowTest(unittest.TestCase):
    def test_real_dex_tables_include_interfaces_and_unicode_identifiers(self):
        self.assertEqual({"Lj$/A;": ("Ljava/lang/Object;", "Lj$/\u00c9;")},
                         audit.dex_hierarchy(dex_fixture()))

    def test_invalid_interface_range_is_rejected(self):
        dex = bytearray(dex_fixture())
        struct.pack_into("<I", dex, 148, len(dex) + 40)
        with self.assertRaises(ValueError):
            audit.dex_hierarchy(bytes(dex))

    def test_duplicate_definitions_across_dex_files_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "duplicate.apk"
            with zipfile.ZipFile(apk, "w") as output:
                output.writestr("classes.dex", dex_fixture())
                output.writestr("classes2.dex", dex_fixture())
            with self.assertRaisesRegex(ValueError, "Duplicate APK"):
                audit.apk_hierarchy(apk)

    def test_no_dex_apk_is_invalid(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "empty.apk"
            with zipfile.ZipFile(apk, "w"):
                pass
            with self.assertRaisesRegex(ValueError, "no DEX"):
                audit.apk_hierarchy(apk)

    def test_independent_test_obfuscation_cannot_break_app_assignability(self):
        app = {"Lj$/time/ZoneOffset;": ("Lj$/time/ZoneId;",),
               "Lj$/time/ZoneId;": ("Ljava/lang/Object;",)}
        test = {"Lj$/time/ZoneOffset;": ("Lj$/time/a0;",),
                "Lj$/time/a0;": ("Ljava/lang/Object;",)}
        with self.assertRaisesRegex(ValueError, "ZoneOffset.*ZoneId"):
            audit.verify_hierarchy(app, test)

    def test_additional_test_only_types_do_not_change_app_contract(self):
        app = {"Lj$/time/ZoneOffset;": ("Lj$/time/ZoneId;",),
               "Lj$/time/ZoneId;": ("Ljava/lang/Object;",)}
        self.assertEqual(2, audit.verify_hierarchy(app, dict(app, extra=("Ljava/lang/Object;",))))

    def test_indirect_parent_and_interface_assignability_are_preserved(self):
        app = {"Lj$/A;": ("Lj$/B;", "Lj$/I;"), "Lj$/B;": ("Lj$/C;",),
               "Lj$/C;": ("Ljava/lang/Object;",), "Lj$/I;": ("Ljava/lang/Object;",)}
        test = {"Lj$/B;": ("Ljava/lang/Object;",)}
        with self.assertRaisesRegex(ValueError, r"Lj\$/A;.*Lj\$/C;"):
            audit.verify_hierarchy(app, test)

    def test_no_shadow_is_valid(self):
        self.assertEqual(0, audit.verify_hierarchy({"Lj$/A;": ("Ljava/lang/Object;",)}, {}))

    def test_parent_cycles_are_invalid(self):
        with self.assertRaisesRegex(ValueError, "cycle"):
            audit.verify_hierarchy({"Lj$/A;": ("Lj$/B;",), "Lj$/B;": ("Lj$/A;",)}, {})

    def test_truncated_dex_is_invalid(self):
        with self.assertRaises(ValueError):
            audit.dex_hierarchy(b"dex\n039\0")


if __name__ == "__main__":
    unittest.main()
