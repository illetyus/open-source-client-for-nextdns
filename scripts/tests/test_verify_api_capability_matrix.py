import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_api_capability_matrix import verify_route_evidence


class ApiRouteEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.routes = {("GET", "/profiles"), ("GET", "/profiles/{profileId}/setup")}
        self.valid_inventory = """<!-- API_ROUTE_EVIDENCE_START -->
| Method | Service-relative route | Evidence class | Source or fixture |
|---|---|---|---|
| `GET` | `/profiles` | `DOCUMENTED` | [S1: Profiles] |
| `GET` | `/profiles/{profileId}/setup` | `DECLARED_ONLY` | `NextDnsApiService.kt`; `NextDnsApiContractTest` |
<!-- API_ROUTE_EVIDENCE_END -->"""

    def test_accepts_complete_unique_routes_with_provenance(self):
        self.assertEqual(self.routes, verify_route_evidence(self.valid_inventory, self.routes))

    def test_rejects_a_missing_route_classification(self):
        incomplete = self.valid_inventory.replace(
            "| `GET` | `/profiles/{profileId}/setup` | `DECLARED_ONLY` | `NextDnsApiService.kt`; `NextDnsApiContractTest` |\n",
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


if __name__ == "__main__":
    unittest.main()
