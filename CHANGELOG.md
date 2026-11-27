# Changelog

All notable project changes intended for users or release consumers are recorded here.

The project follows [Semantic Versioning](https://semver.org/) for GitHub release tags. GitHub-generated release notes supplement this file with pull-request-level details.

## [Unreleased]

## [1.0.0-beta.2] - 2026-11-27

### Security

- Validate Retrofit `@Path` segments (`profileId`, `domain`, `tld`, and catalog/rule IDs) against empty values, dot-segments, and separator characters before network requests.
- Recover cleanly from invalidated or corrupted Android Keystore entries in `AndroidKeystoreApiKeyProtector` and synchronize `NextDnsPreferences.apiKey` reads, writes, and clears.
- Upgrade OkHttp (`okhttp`, `logging-interceptor`, `mockwebserver`, `okhttp-tls`) to `4.12.0` (Okio `3.7.0`, addressing CVE-2023-3635).
- Add a 10-second auto-hide timer for revealed API keys on `LoginScreen` to match `ApiConnectionSettingsSection` and `docs/SECURITY_THREAT_MODEL.md`.
- Clear `NotificationPreferences` account state when switching to a different API key via `loginWithApiKey` without a prior explicit logout.
- Document the `clearLogs()` snapshot-generation exception in `docs/SECURITY_THREAT_MODEL.md`.

### Build & Dependencies

- Enable Gradle dependency locking for `releaseRuntimeClasspath` (`app/gradle.lockfile`) so CI `osv-scanner` scans resolved dependencies.
- Add `gradle` ecosystem monitoring to `.github/dependabot.yml`.

### Added

- Release-grade repository presentation with product-focused README content and CI-generated interface screenshots.
- Community health files for contributions, security reporting, issue intake, and pull requests.
- Tag-driven GitHub release workflow for signed APK/AAB output, SHA-256 checksums, and artifact attestations.
- Separate release-candidate and stable-release gates so draft legal metadata cannot accidentally ship as a stable release.
- Curated GitHub release-note categories in `.github/release.yml`.

### Release candidate baseline

The first `1.0` line includes NextDNS profile/configuration management, security/privacy/parental-control settings, allow/deny rules, logs and analytics, diagnostics, notifications, export flows, five interface languages, and the repository's release/security verification suite.
