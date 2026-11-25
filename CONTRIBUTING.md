# Contributing

Thanks for considering a contribution to Open Source Client for NextDNS.

## Before opening a pull request

1. Search existing issues and pull requests for related work.
2. Keep changes focused; avoid mixing unrelated refactors with feature or bug-fix work.
3. Never commit API keys, DNS logs, real profile identifiers, signing files, or private test data.
4. Preserve the project's independent/unofficial positioning and do not introduce official NextDNS branding assets.
5. Do not remove release, privacy, legal, or security gates merely to make a build pass.

## Development environment

The CI baseline uses JDK 17 and the Gradle Wrapper committed to the repository.

```bash
chmod +x gradlew
./gradlew --version
./gradlew :app:assembleDebug --no-daemon
```

## Verification

For normal Android changes, run at least:

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest --no-daemon
python3 scripts/verify_localization.py
python3 scripts/verify_api_capability_matrix.py
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
```

Changes that affect release behavior, legal assets, networking, security, dependencies, packaging, or store metadata must also run the relevant repository verification scripts and pass CI.

## Pull requests

A useful pull request explains:

- the problem being solved;
- the behavior before and after the change;
- the verification performed;
- any user-visible, privacy, security, localization, or release impact.

Use the pull request template and keep screenshots/log excerpts free of sensitive data.

## Style and scope

- Follow the existing Kotlin and Compose conventions in the codebase.
- Prefer small, reviewable changes with tests for behavior changes.
- Keep user-facing strings in Android resources and maintain all supported locales where applicable.
- Keep documentation and public repository copy in English unless a document is intentionally locale-specific or a historical compliance record.

## Security reports

Do not disclose vulnerabilities or exploit details in a public issue. Follow [SECURITY.md](SECURITY.md).
