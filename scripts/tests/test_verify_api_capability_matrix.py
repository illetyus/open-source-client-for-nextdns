import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_api_capability_matrix import (
    DOCUMENTED_PROFILE_FIELDS,
    OBSERVED_SECURITY_FIELDS,
    verify_profile_fields,
    verify_route_evidence,
)


class ApiRouteEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.routes = {("GET", "/profiles"), ("GET", "/profiles/{profileId}/settings")}
        self.valid_inventory = """<!-- API_ROUTE_EVIDENCE_START -->
| Method | Service-relative route | Evidence class | Source or fixture |
|---|---|---|---|
| `GET` | `/profiles` | `DOCUMENTED` | [S1: Profiles] |
| `GET` | `/profiles/{profileId}/settings` | `DOCUMENTED` | [S1: Profile settings] |
<!-- API_ROUTE_EVIDENCE_END -->"""

    def test_accepts_complete_unique_routes_with_provenance(self):
        self.assertEqual(self.routes, verify_route_evidence(self.valid_inventory, self.routes))

    def test_rejects_a_missing_route_classification(self):
        incomplete = self.valid_inventory.replace(
            "| `GET` | `/profiles/{profileId}/settings` | `DOCUMENTED` | [S1: Profile settings] |\n",
            "",
        )
        with self.assertRaisesRegex(AssertionError, "Missing classifications"):
            verify_route_evidence(incomplete, self.routes)

    def test_rejects_duplicate_route_classification(self):
        duplicate = self.valid_inventory.replace(
            "<!-- API_ROUTE_EVIDENCE_END -->",
            "| `GET` | `/profiles` | `OBSERVED` | sanitized response fixture |\n<!-- API_ROUTE_EVIDENCE_END -->",
        )
        with self.assertRaisesRegex(AssertionError, "Duplicate route classifications"):
            verify_route_evidence(duplicate, self.routes)

    def test_rejects_classifications_without_a_source_or_fixture(self):
        missing_source = self.valid_inventory.replace(
            "[S1: Profiles]", ""
        )
        with self.assertRaisesRegex(AssertionError, "source or fixture reference"):
            verify_route_evidence(missing_source, self.routes)


class ApiProfileFieldInventoryTest(unittest.TestCase):
    def setUp(self):
        self.fields = DOCUMENTED_PROFILE_FIELDS | OBSERVED_SECURITY_FIELDS
        self.inventory = "\n".join(
            f"| `{field}` | `DTO` | `UI` | observed or documented |" for field in sorted(self.fields)
        )

    def test_accepts_published_fields_and_observed_security_fields(self):
        self.assertEqual(self.fields, verify_profile_fields(self.inventory))

    def test_rejects_a_missing_observed_security_field(self):
        inventory = self.inventory.replace("`security.dnsDataExfiltration`", "`security.notPresent`")
        with self.assertRaisesRegex(AssertionError, "security.dnsDataExfiltration"):
            verify_profile_fields(inventory)

    def test_rejects_an_unobserved_field(self):
        inventory = self.inventory + "\n| `security.futureGuess` | `DTO` | `UI` | guessed |"
        with self.assertRaisesRegex(AssertionError, "security.futureGuess"):
            verify_profile_fields(inventory)


if __name__ == "__main__":
    unittest.main()
