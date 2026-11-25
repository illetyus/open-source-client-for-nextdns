# Documentation index

The repository keeps the root README concise and product-oriented. Detailed engineering, release, compliance, provenance, and historical records live under `docs/`.

## Build and verify

Building the app requires JDK 17 and an Android SDK that matches `compileSdk` in [app/build.gradle.kts](../app/build.gradle.kts). Run the following commands from the repository root. On Windows, use `gradlew.bat` in place of `./gradlew`.

```bash
./gradlew :app:assembleDebug --no-daemon
./gradlew :app:lintDebug :app:testDebugUnitTest --no-daemon
python3 scripts/verify_localization.py
python3 scripts/verify_api_capability_matrix.py
python3 scripts/verify_legal_assets.py
```

The [Android CI workflow](../.github/workflows/ci.yml) also checks package contents, dependency security, and the release app against local network fixtures. The [release workflow](../.github/workflows/release.yml) documents signing, checksums, and build provenance for automated GitHub releases. [Releasing](RELEASING.md) describes the corresponding publication checks.

## Engineering reference

- [Releasing](RELEASING.md) — versioning, signing secrets, release-candidate/stable gates, and GitHub release flow.
- [Security threat model](SECURITY_THREAT_MODEL.md) — application trust boundaries and security assumptions.
- [Source and license provenance](SOURCE_AND_LICENSE_PROVENANCE.md) — dependency and licensing records.
- [Brand asset provenance](BRAND_ASSET_PROVENANCE.md) — origin and scope of project-authored visual assets.

## Legal and publication material

`docs/legal/` contains draft legal material and research used for release preparation. Draft documents are not publication approval and must not be represented as final policy until the repository's stable-release legal gates pass.

## Release evidence

`docs/release/` contains package, device, BrowserStack, license, and signed-candidate review records. These files preserve evidence; they are not substitutes for the automated release gates.

## Historical and implementation records

The [project phase ledger](PROJECT_PHASE_STATUS.md) and [bug log](HATA_DEFTERI_TR.md) retain dated results, reproduction notes, and links to verification records. Some of those records are written in Turkish. The repository introduction is available in [English](../README.md) and [Turkish](../README.tr.md).
