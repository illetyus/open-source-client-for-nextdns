"""Shared contract for release runtime evidence consumers."""
import re

REQUIRED_RUNTIME_TESTS = 18


def instrumentation_failure_summary(text):
    """Return compact JUnit failures with their cause and relevant app frames."""
    diagnostics = []
    current = None
    app_frames = 0

    def flush():
        if current:
            diagnostics.extend(current)

    for line in text.splitlines():
        if re.match(r'^\s*\d+\)\s+', line):
            flush()
            current = [line.strip()]
            app_frames = 0
            continue
        if current is None:
            continue
        if re.match(r'^\s*Tests run:', line):
            current.append(line.strip())
            flush()
            current = None
            continue
        stripped = line.strip()
        if re.match(r'^(?:java\.lang\.|kotlin(?:x)?\.|androidx\.|com\.example\.|com\.squareup\.|okhttp3\.|okio\.|retrofit2\.|Caused by:)', stripped):
            current.append(stripped)
        elif re.match(r'^at (?:com\.example\.|kotlin(?:x)?\.|androidx\.|com\.squareup\.|okhttp3\.|okio\.|retrofit2\.)', stripped):
            if app_frames < 5:
                current.append(stripped)
                app_frames += 1
    flush()
    return '\n'.join(diagnostics) if diagnostics else 'No JUnit failure details were emitted.'


def validate_runtime_summary(runtime: dict) -> None:
    if not isinstance(runtime, dict):
        raise ValueError("runtime evidence must be a JSON object")
    if type(runtime.get("tests")) is not int or runtime["tests"] != REQUIRED_RUNTIME_TESTS:
        raise ValueError(f"runtime evidence must contain exactly {REQUIRED_RUNTIME_TESTS} tests")
    if type(runtime.get("failures")) is not int or runtime["failures"] != 0:
        raise ValueError("runtime evidence must contain zero failures")
    if type(runtime.get("skipped")) is not int or runtime["skipped"] != 0:
        raise ValueError("runtime evidence must contain zero skipped tests")
