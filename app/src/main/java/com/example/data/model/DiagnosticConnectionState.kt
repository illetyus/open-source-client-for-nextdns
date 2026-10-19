package com.example.data.model

enum class DiagnosticConnectionState {
  TESTING, ERROR, UNCONFIGURED, MATCHED_PROFILE, UNVERIFIED_PROFILE, NO_SELECTED_PROFILE
}

fun diagnosticConnectionState(
  selectedProfile: NextDnsProfile?,
  result: DiagnosticTestResult,
  running: Boolean = false
): DiagnosticConnectionState = when {
  running || result.isTesting -> DiagnosticConnectionState.TESTING
  result.errorMessage != null || result.status.equals("error", true) -> DiagnosticConnectionState.ERROR
  result.lastTestedTime <= 0L -> DiagnosticConnectionState.UNCONFIGURED
  result.status.lowercase().trim() !in setOf("ok", "using-nextdns") -> DiagnosticConnectionState.UNCONFIGURED
  selectedProfile == null || selectedProfile.id.isBlank() -> DiagnosticConnectionState.NO_SELECTED_PROFILE
  selectedProfile.fingerprint.isBlank() || !result.profileId.trim().equals(selectedProfile.fingerprint.trim(), true) -> DiagnosticConnectionState.UNVERIFIED_PROFILE
  else -> DiagnosticConnectionState.MATCHED_PROFILE
}
