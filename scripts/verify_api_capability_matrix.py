"""Require the documented endpoint inventory to stay aligned with Retrofit."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/com/example/data/api/NextDnsApiService.kt"
MATRIX = ROOT / "docs/NEXTDNS_API_CAPABILITY_MATRIX_TR.md"
START = "<!-- API_ROUTE_INVENTORY_START -->"
END = "<!-- API_ROUTE_INVENTORY_END -->"
ROUTE_ANNOTATION = re.compile(r'@(GET|POST|PATCH|DELETE)\("([^"]+)"\)')
ROUTE_ROW = re.compile(r"^\| `([A-Z]+)` \| `([^`]+)` \|$", re.MULTILINE)


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
    print(f"PASS: API capability matrix lists all {len(implemented)} Retrofit routes exactly once")


if __name__ == "__main__":
    verify()

