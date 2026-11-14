"""Require the documented endpoint inventory to stay aligned with Retrofit."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/com/example/data/api/NextDnsApiService.kt"
MATRIX = ROOT / "docs/NEXTDNS_API_CAPABILITY_MATRIX_TR.md"
START = "<!-- API_ROUTE_INVENTORY_START -->"
END = "<!-- API_ROUTE_INVENTORY_END -->"
EVIDENCE_START = "<!-- API_ROUTE_EVIDENCE_START -->"
EVIDENCE_END = "<!-- API_ROUTE_EVIDENCE_END -->"
FIELD_START = "<!-- API_PROFILE_FIELD_INVENTORY_START -->"
FIELD_END = "<!-- API_PROFILE_FIELD_INVENTORY_END -->"
ROUTE_ANNOTATION = re.compile(r'@(GET|POST|PATCH|DELETE)\("([^"]+)"\)')
ROUTE_ROW = re.compile(r"^\| `([A-Z]+)` \| `([^`]+)` \|$", re.MULTILINE)
FIELD_ROW = re.compile(r"^\| `([^`]+)` \|", re.MULTILINE)
EVIDENCE_ROW = re.compile(r"^\| `([A-Z]+)` \| `([^`]+)` \| `([^`]+)` \| (.*) \|$")

DOCUMENTED_PROFILE_FIELDS = {
    "name",
    "security.threatIntelligenceFeeds",
    "security.aiThreatDetection",
    "security.googleSafeBrowsing",
    "security.cryptojacking",
    "security.dnsRebinding",
    "security.idnHomographs",
    "security.typosquatting",
    "security.dga",
    "security.nrd",
    "security.ddns",
    "security.parking",
    "security.csam",
    "security.tlds[].id",
    "privacy.blocklists[].id",
    "privacy.natives[].id",
    "privacy.disguisedTrackers",
    "privacy.allowAffiliate",
    "parentalControl.services[].id",
    "parentalControl.services[].active",
    "parentalControl.categories[].id",
    "parentalControl.categories[].active",
    "parentalControl.safeSearch",
    "parentalControl.youtubeRestrictedMode",
    "parentalControl.blockBypass",
    "denylist[].id",
    "denylist[].active",
    "allowlist[].id",
    "allowlist[].active",
    "settings.logs.enabled",
    "settings.logs.drop.ip",
    "settings.logs.drop.domain",
    "settings.logs.retention",
    "settings.logs.location",
    "settings.blockPage.enabled",
    "settings.performance.ecs",
    "settings.performance.cacheBoost",
    "settings.performance.cnameFlattening",
    "settings.web3",
}

# These Security booleans were observed in the user's real profile response UI;
# they are intentionally separate from the currently published profile example.
OBSERVED_SECURITY_FIELDS = {
    "security.dataDropServices",
    "security.decentralizedWebGateways",
    "security.dnsDataExfiltration",
    "security.dnsPayloadDelivery",
    "security.fastFluxNetworks",
    "security.freeHostingDomains",
    "security.highRiskTlds",
    "security.newlyActiveDomains",
    "security.residentialHosting",
    "security.tunnelingEndpoints",
    "security.untrustedCertificates",
}


def verify_route_evidence(matrix_text, implemented):
    assert matrix_text.count(EVIDENCE_START) == 1 and matrix_text.count(EVIDENCE_END) == 1, (
        "API route evidence markers must appear exactly once"
    )
    inventory = matrix_text.split(EVIDENCE_START, 1)[1].split(EVIDENCE_END, 1)[0]
    table_lines = [line for line in inventory.splitlines() if line.startswith("|")]
    assert len(table_lines) >= 2 and table_lines[0].startswith("| Method |"), (
        "API route evidence inventory must contain its four-column header"
    )

    rows = []
    allowed_classes = {"DOCUMENTED", "OBSERVED", "DECLARED_ONLY"}
    for line in table_lines[2:]:
        match = EVIDENCE_ROW.fullmatch(line)
        assert match, f"Malformed API route evidence row: {line}"
        method, path, evidence_class, source = match.groups()
        assert evidence_class in allowed_classes, f"Unknown API route evidence class: {evidence_class}"
        assert source.strip() and source.strip().lower() not in {"-", "tbd", "unknown"}, (
            f"Each API route evidence row needs a source or fixture reference: {method} {path}"
        )
        rows.append((method, path))

    assert len(rows) == len(set(rows)), "Duplicate route classifications in the API evidence matrix"
    classified = set(rows)
    missing = sorted(implemented - classified)
    stale = sorted(classified - implemented)
    assert not missing and not stale, (
        f"API evidence and Retrofit differ. Missing classifications: {missing}; stale classifications: {stale}"
    )
    return classified


def verify_profile_fields(field_inventory):
    table_lines = [line for line in field_inventory.splitlines() if line.startswith("|")]
    assert table_lines and all(line.count("|") == 5 for line in table_lines), (
        "Each profile-field inventory row must contain exactly four Markdown columns"
    )
    documented_fields = FIELD_ROW.findall(field_inventory)
    assert len(documented_fields) == len(set(documented_fields)), "Duplicate profile fields in the API matrix"
    expected_fields = DOCUMENTED_PROFILE_FIELDS | OBSERVED_SECURITY_FIELDS
    missing_fields = sorted(expected_fields - set(documented_fields))
    stale_fields = sorted(set(documented_fields) - expected_fields)
    assert not missing_fields and not stale_fields, (
        "API profile-field matrix differs from published and observed profile fields. "
        f"Missing: {missing_fields}; extra: {stale_fields}"
    )
    return set(documented_fields)


def verify():
    service_text = SERVICE.read_text(encoding="utf-8")
    matrix_text = MATRIX.read_text(encoding="utf-8")
    assert matrix_text.count(START) == 1 and matrix_text.count(END) == 1, (
        "API endpoint inventory markers must appear exactly once"
    )
    inventory = matrix_text.split(START, 1)[1].split(END, 1)[0]
    documented = set(ROUTE_ROW.findall(inventory))
    assert len(documented) == len(ROUTE_ROW.findall(inventory)), "Duplicate endpoint rows in the API matrix"
    implemented = {
        (method, "/" + path.lstrip("/"))
        for method, path in ROUTE_ANNOTATION.findall(service_text)
    }
    missing = sorted(implemented - documented)
    stale = sorted(documented - implemented)
    assert not missing and not stale, (
        f"API matrix and Retrofit differ. Missing from matrix: {missing}; stale matrix rows: {stale}"
    )
    verify_route_evidence(matrix_text, implemented)
    assert matrix_text.count(FIELD_START) == 1 and matrix_text.count(FIELD_END) == 1, (
        "API profile-field inventory markers must appear exactly once"
    )
    field_inventory = matrix_text.split(FIELD_START, 1)[1].split(FIELD_END, 1)[0]
    documented_fields = verify_profile_fields(field_inventory)
    print(
        f"PASS: API capability matrix classifies all {len(implemented)} Retrofit routes "
        f"and lists {len(documented_fields)} published or observed profile fields exactly once"
    )


if __name__ == "__main__":
    verify()

