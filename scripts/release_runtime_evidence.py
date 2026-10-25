"""Shared contract for release runtime evidence consumers."""

REQUIRED_RUNTIME_TESTS = 16


def validate_runtime_summary(runtime: dict) -> None:
    if not isinstance(runtime, dict):
        raise ValueError("runtime evidence must be a JSON object")
    if type(runtime.get("tests")) is not int or runtime["tests"] != REQUIRED_RUNTIME_TESTS:
        raise ValueError(f"runtime evidence must contain exactly {REQUIRED_RUNTIME_TESTS} tests")
    if type(runtime.get("failures")) is not int or runtime["failures"] != 0:
        raise ValueError("runtime evidence must contain zero failures")
    if type(runtime.get("skipped")) is not int or runtime["skipped"] != 0:
        raise ValueError("runtime evidence must contain zero skipped tests")
