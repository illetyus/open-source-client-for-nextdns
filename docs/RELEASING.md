# Releasing

This document defines the GitHub release process for Open Source Client for NextDNS.

## Versioning

GitHub tags use Semantic Versioning:

- Release candidate: `v1.0.0-rc.1`, `v1.0.0-rc.2`, ...
- Stable release: `v1.0.0`

The workflow compares the base tag version with `versionName` in `app/build.gradle.kts`. A two-component Android version such as `1.0` is normalized to `1.0.0` for this comparison.

## Release types

### Release candidate

A tag containing a pre-release suffix (for example `-rc.1`) creates a GitHub **pre-release**. It may be used while final publisher/legal publication metadata is still marked as draft, but it must still pass the repository's draft-safe release checks.

### Stable release

A stable tag has no pre-release suffix. Stable publication is fail-closed: the workflow runs the final publication and legal-release checks and stops if draft markers, placeholders, or other release blockers remain.

This distinction is deliberate. A feature-complete application is not the same thing as a legally and operationally approved public stable release.

## Required GitHub Actions secrets

The release job requires an Android upload/release keystore. Configure these repository secrets before pushing a release tag:

- `ANDROID_KEYSTORE_BASE64` — base64-encoded keystore bytes.
- `ANDROID_KEYSTORE_PASSWORD` — keystore password.
- `ANDROID_KEY_ALIAS` — signing key alias.
- `ANDROID_KEY_PASSWORD` — key password.

The workflow decodes the keystore into the runner's temporary directory and never commits signing material to the repository.

## Preflight

Before creating a tag:

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest --no-daemon
python3 scripts/verify_localization.py
python3 scripts/verify_api_capability_matrix.py
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
```

For a release candidate:

```bash
python3 scripts/verify_publication_ready.py --draft
python3 scripts/verify_legal_assets.py
```

For a stable release:

```bash
python3 scripts/verify_publication_ready.py
bash scripts/verify_legal_release_ready.sh
```

## Publishing

After the release commit is merged to `main`, create and push the tag from that exact commit:

```bash
git checkout main
git pull --ff-only
git tag v1.0.0-rc.1
git push origin v1.0.0-rc.1
```

For a stable release, use the stable tag instead:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The `Release` GitHub Actions workflow then:

1. validates the tag against the Android version;
2. runs the appropriate RC or stable publication gate;
3. runs lint and unit tests;
4. reconstructs the signing keystore from repository secrets;
5. builds signed release APK and AAB artifacts;
6. verifies the APK/AAB signatures;
7. produces `SHA256SUMS.txt`;
8. creates GitHub artifact attestations;
9. uploads the build as a workflow artifact;
10. creates the GitHub Release and generates categorized release notes.

## Release assets

Expected published assets:

- `open-source-client-for-nextdns-<tag>.apk`
- `open-source-client-for-nextdns-<tag>.aab`
- `SHA256SUMS.txt`

CI-generated unsigned release artifacts remain review evidence only and are not production downloads.

## Google Play

GitHub release preparation does not by itself publish to Google Play. Store publication remains a separate controlled step and must use the final publisher identity, support/privacy contact, effective dates, jurisdiction-specific legal review, privacy URL, Data Safety declarations, and Play testing evidence required by the project's publication gates.
