"""Require executed regression cases for tracked API/UI defects and favicon handling; emit JVM evidence."""
from pathlib import Path
import hashlib
import json
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = "com.example.data.repository.RepositoryRegressionTest"
NETWORK = "com.example.data.api.CancellableCallTest"
DOCUMENT = "com.example.data.repository.ExportDocumentTest"
UI = "com.example.ui.screens.DiagnosticAndDeviceMenuTest"
POLLING = "com.example.ui.screens.DiagnosticPollingTest"
AUDIT_REPOSITORY = "com.example.audit.RepositoryAuditTest"
AUDIT_UI = "com.example.audit.UiAuditTest"
IDENTITY = "com.example.audit.InputAndIdentityTest"
ACCOUNT = "com.example.audit.AccountSettingsFlowTest"
FAVICON = "com.example.ui.screens.NextDnsFaviconNetworkTest"
RETIRED_SPEED = "com.example.data.local.RetiredFeaturePreferencesTest"
API_CONTRACT = "com.example.data.api.NextDnsApiContractTest"
SNAPSHOT = "com.example.data.notifications.ConfigSnapshotHasherTest"

REQUIRED = {
    "NDNS-001": [(REPOSITORY, name) for name in (
        "lateLogsCannotPopulateAnotherProfile", "profileRoundTripStillRejectsOldLogs",
        "lateCreatedProfileCannotRestorePreferencesAfterLogout", "obsoleteSettingsCannotWriteAnotherProfilesCache",
        "normalCreatedProfileIsVerifiedPersistedAndSelected")],
    "NDNS-002": [(REPOSITORY, name) for name in (
        "mutationForACannotBeVerifiedUsingB", "successfulMutationReadsAndCachesTheSameProfile",
        "successfulHttpReadDoesNotVerifyAnUnappliedMutation",
        "deletingSelectedProfileVerifiesRemovalBeforeSelectingReplacement",
        "deletingLastProfileVerifiesEmptyListAndClearsSelection",
        "acceptedDeleteDoesNotSucceedWhenServerStillContainsTarget")],
    "NDNS-003": [(REPOSITORY, "diagnosticTimeoutDoesNotReplaceLastSuccessWithFreshSuccess"),
        (NETWORK, "diagnosticHttpFailureIgnoresSuccessLookingJson"),
        (UI, "diagnosticBannerRequiresSuccessfulMeasurementOfSelectedProfile")],
    "NDNS-004": [(REPOSITORY, "web3TrueFalseAndMissingFieldPreserveAuthoritativeState")],
    "NDNS-005": [(POLLING, "pauseStopsPollingAndCancelsInFlightCheckThenResumeStartsOneLoop"),
        (REPOSITORY, "cancelledDiagnosticClearsBothRepositoryAndViewModelBusyFlags")],
    "NDNS-006": [(NETWORK, "cancellationBeforeHeadersClosesLateDeliveredResponse"),
        (NETWORK, "cancellationDuringBlockingBodyReadCancelsCallAndClosesBody"),
        (REPOSITORY, "stoppingRepositorySseCancelsTheActualNetworkCall")],
    "NDNS-007": [(API_CONTRACT, "undocumentedSetupAndLinkedIpRoutesAreNotDeclared"),
        (NETWORK, "allCatalogHttpFailurePathsCloseTheirUnconsumedBodies"),
        (NETWORK, "malformedDiagnosticJsonAlsoClosesBody"),
        (NETWORK, "exceptionInsideConsumerStillClosesResponse")],
    "NDNS-008": [(REPOSITORY, name) for name in (
        "cancellingRefreshClearsItsBusyState", "oldRefreshCancellationCannotClearNewRefreshFlag",
        "differentSectionRefreshesKeepBothFlagsAndResults", "cancellingFullSyncClearsBusyStateWithoutStartingAnalytics")],
    "NDNS-009": [(UI, "allDevicesMenuIsLocalizedWhileRealDeviceNamesRemainUnchanged")],
    "NDNS-010": [(REPOSITORY, "malformedExportLinkReturnsFailureInsteadOfThrowing"),
        (REPOSITORY, "validExportUsesNoApiKeyAndOutputWriteFailureReturnsFailure"),
        (REPOSITORY, "providerFailureIsReportedByViewModelWithoutSuccess")] +
        [(DOCUMENT, name) for name in ("deniedProviderReturnsFailureWithoutWriting",
        "nullProviderStreamReturnsFailure", "closeFailurePreventsSuccess",
        "cancellationPropagatesAndClosesProviderStream", "successIncludesProviderCloseAndExpectedBytes")],
    "NDNS-011": [("com.example.data.legal.LegalWelcomeViewportTest",
        "shortLandscapeAtLargeFontKeepsDocumentsAndConsentReachableInFiveLanguages")],
    "NDNS-012": [(AUDIT_UI, "identicalRealisticFingerprintMustShowConnectedForSelectedProfile"),
        (AUDIT_UI, "missingFingerprintMustNotVerifyTheSelectedProfile"),
        (IDENTITY, "diagnosticIdentityUsesFingerprintWhileErrorsAndMissingIdentityRemainUnverified")],
    "NDNS-013": [(AUDIT_UI, "endpointsMustUseTheValidatedProfileIpv6Format"),
        (AUDIT_UI, "copiedIpv6MustMatchBothDisplayedServiceAddresses"),
        (IDENTITY, "profileIpv6EndpointsComeFromTheValidatedProfileId")],
    "NDNS-014": [(AUDIT_UI, name) for name in (
        "failedAllowlistSubmissionMustPreserveTypedDomain", "failedDenylistSubmissionMustPreserveTypedDomain",
        "successfulAllowlistSubmissionClearsOnlyTheConfirmedText", "successfulDenylistSubmissionClearsOnlyTheConfirmedText",
        "lateAllowlistSuccessMustPreserveNewText", "lateDenylistSuccessMustPreserveNewText")],
    "NDNS-015": [(AUDIT_REPOSITORY, name) for name in (
        "eachFailedAnalyticsPanelIsReportedAsPartialWhileSuccessfulPanelsAdvance",
        "failedCountryPanelRetainsItsLastSuccessfulSnapshot",
        "malformedOrSemanticAnalyticsFailureDoesNotLookSuccessful")],
    "NDNS-016": [(AUDIT_REPOSITORY, name) for name in (
        "olderAnalyticsFilterResponseMustNotOverwriteNewSelection",
        "obsoleteAnalyticsFailureCannotReplaceNewSelectionSuccess",
        "cancellingOldAnalyticsCannotClearNewRequestLoading")],
    "NDNS-017": [(AUDIT_REPOSITORY, name) for name in (
        "emptyAuthoritativeLogRefreshMustRemoveDeletedRemoteHistory",
        "invalidLogSnapshotPreservesHistoryAndNewerRefreshWins",
        "inFlightLogRefreshCannotRestoreHistoryAfterSuccessfulClear")],
    "NDNS-018": [(AUDIT_REPOSITORY, "choosingGuestMustSupersedeAnInFlightLogin")],
    "NDNS-019": [(AUDIT_REPOSITORY, name) for name in (
        "malformedDomainRulesCannotBypassTheForms",
        "unicodeAndWildcardRulesUseCanonicalIdsForWriteAndVerification")]+
        [(AUDIT_UI, name) for name in ("validPunycodeMustReachTheRepository", "malformedLabelsMustNotReachTheRepository")]+
        [(IDENTITY, name) for name in (
            "internationalDomainsAndWildcardRulesNormalizeToAscii",
            "invalidLabelsUrlsIpsAndOversizedRulesAreRejected")],
"NDNS-047": [(ACCOUNT, "fullSyncReportsPartialAnalyticsWhileKeepingSuccessfulPanels")],
"NDNS-060": [(REPOSITORY, "oldSearchStreamCannotLeakRowsIntoNewSearchResults")],
"NDNS-061": [(ACCOUNT, "speedTestEntryIsAbsentFromAccountScreen"),
    (RETIRED_SPEED, "retiredSpeedSettingsAreClearedWithoutTouchingCredentialsThemeLocaleOrLegalAcceptance")],
"NDNS-062": [(RETIRED_SPEED, "retiredSpeedSettingsCleanupIsIdempotent")],
"NDNS-063": [(API_CONTRACT, name) for name in (
    "securityResponseRetainsUnknownJsonForReadOnlyDiscovery",
    "securityUnknownFieldRetentionIsBoundedAndOverflowIsFingerprintable",
    "oversizedSecurityResponseIsRejectedWhenLengthIsKnown",
    "oversizedChunkedSecurityResponseIsRejectedWithoutContentLength")]+
    [(SNAPSHOT, name) for name in (
        "digestChangesWhenAnUnverifiedServerSecurityFieldChanges",
        "digestRemainsOrderIndependentForNestedUnknownServerFields")]+
    [(AUDIT_UI, "unverifiedSecurityFieldsAreDisclosedReadOnlyWithoutShowingRawValues")],
"NDNS-054": [(FAVICON, name) for name in (
    "validImageLoadsAndIsCachedInMemory",
    "redirectIsRejectedWithoutRequestingItsTarget",
    "nonImageMimeTypeIsRejected",
    "oversizedChunkedBodyIsRejectedEvenWithoutContentLength",
    "cleartextUrlIsRejectedBeforeAnyRequest",
    "unexpectedHostIsRejectedBeforeAnyRequest",
    "malformedImageBytesAreRejected",
    "wholeCallTimeoutReturnsFallbackResult",
    "cancellingLoadCancelsTheUnderlyingOkHttpCall")],
    "NDNS-065": [(REPOSITORY, "unknownLogStorageLocationDoesNotMasqueradeAsThePreviousLocalValue")],
    "NDNS-073": [(AUDIT_REPOSITORY, "analyticsDevicePaginationIncludesEveryFilterableDevice"),
        (AUDIT_REPOSITORY, "repeatedAnalyticsDeviceCursorKeepsTheLastCompleteInventory"),
        (AUDIT_REPOSITORY, "analyticsDevicePageLimitKeepsTheLastCompleteInventory")],
}


def changed_app_sources(root):
    tracked = subprocess.check_output(
        ["git", "diff", "--name-only", "HEAD", "--", "app/src"],
        cwd=root,
        text=True,
    ).splitlines()
    untracked = subprocess.check_output(
        ["git", "ls-files", "--others", "--exclude-standard", "--", "app/src"],
        cwd=root,
        text=True,
    ).splitlines()
    return sorted(set(tracked + untracked))


def assert_reports_cover_changed_sources(root, reports, changed_paths):
    if not changed_paths:
        return

    missing = [path for path in changed_paths if not (root / path).is_file()]
    assert not missing, f"Changed or deleted app sources require fresh JVM tests: {missing}"

    newest_source = max((root / path).stat().st_mtime for path in changed_paths)
    oldest_report = min(path.stat().st_mtime for path in reports)
    assert oldest_report >= newest_source, (
        "JVM test reports predate changed app sources; run :app:testDebugUnitTest before "
        "recording quality evidence"
    )


def source_tree_sha256(root):
    digest = hashlib.sha256()
    for path in sorted((root / "app/src").rglob("*")):
        if path.is_file():
            digest.update(path.relative_to(root).as_posix().encode("utf-8"))
            digest.update(b"\0")
            digest.update(path.read_bytes())
            digest.update(b"\0")
    return digest.hexdigest()


def verify(root=ROOT):
    reports = sorted((root / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
    assert reports, "No executed JVM test reports"
    changed_sources = changed_app_sources(root)
    assert_reports_cover_changed_sources(root, reports, changed_sources)
    executed = set()
    hashes = {}
    for path in reports:
        suite = ET.parse(path).getroot()
        assert int(suite.get("failures", 0)) + int(suite.get("errors", 0)) == 0, path.name
        assert int(suite.get("skipped", 0)) == 0, path.name
        hashes[str(path.relative_to(root))] = hashlib.sha256(path.read_bytes()).hexdigest()
        for case in suite.findall("testcase"):
            assert not any(case.find(tag) is not None for tag in ("failure", "error", "skipped"))
            executed.add((case.get("classname"), case.get("name")))
    for defect, cases in REQUIRED.items():
        missing = set(cases) - executed
        assert not missing, f"{defect} regression did not execute: {sorted(missing)}"
    evidence = {
        "schemaVersion": 1,
        "builtCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(),
        "sourceTreeSha256": source_tree_sha256(root),
        "workingTreeSourceChanges": changed_sources,
        "verifiedAtEpochSeconds": int(time.time()),
        "evidenceKind": "JVM_ROBOLECTRIC_COMPOSE_AND_CONTROLLED_NETWORK_FIXTURES",
        "physicalDeviceAcceptance": "NOT_RUN",
        "realAccountMutation": "NOT_RUN",
        "allExecutedCases": len(executed),
        "requiredUniqueCases": len({case for cases in REQUIRED.values() for case in cases}),
        "defects": {defect: {"result": "PASS", "cases": [
            {"class": classname, "name": name} for classname, name in cases
        ]} for defect, cases in REQUIRED.items()},
        "xmlSha256": hashes,
    }
    output = root / "build/compliance/quality-regressions.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"PASS: all {len(REQUIRED)} defect regressions executed; {evidence['requiredUniqueCases']} required cases; {len(executed)} JVM cases total")
    return evidence


if __name__ == "__main__":
    verify()

