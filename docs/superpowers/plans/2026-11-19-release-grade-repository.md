# Release-grade Repository Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the repository into a professional first-release surface with verified visuals, community health documentation, and secure tag-driven GitHub release automation.

**Architecture:** Keep application code unchanged. Separate the public product surface (README/community files) from deep evidence records, and add one dedicated release workflow that reuses existing verification gates while applying stricter stable-release checks than release-candidate checks.

**Tech Stack:** GitHub Markdown, GitHub issue forms, GitHub Actions, Gradle/Android, Bash, Python repository checks, GitHub CLI, GitHub artifact attestations.

**Spec:** `docs/superpowers/specs/2026-11-19-release-grade-repository-design.md`

## Global Constraints

- Public-facing repository language: English.
- Preserve independent/unofficial NextDNS positioning.
- Do not invent final publisher/legal/contact data.
- Stable release must fail while draft legal/publication markers remain.
- Release APK/AAB output must be signed.
- GitHub Actions dependencies must be pinned to commit SHAs.

## Review Focus

- Stable tag while legal files remain DRAFT must fail before release publication.
- RC tag must be marked as a GitHub pre-release, not stable/latest production output.
- Missing signing secrets must fail closed without printing secret values.
- Tag base version must match the Android `versionName` after normalization.
- Public issue/reporting flows must not encourage users to post sensitive NextDNS account data.

---

### Task 1: Product-facing repository surface

**Files:**
- Modify: `README.md`
- Modify: `metadata.json`
- Create: `docs/README.md`
- Create: `docs/assets/readme/home-en.webp`
- Create: `docs/assets/readme/accessibility-large-text.webp`

- [ ] Replace the development-ledger README with a concise product-oriented English README.
- [ ] Add CI-generated English screenshots as durable repository assets.
- [ ] Keep release/legal status accurate and link deep records through a documentation index.
- [ ] Verify every linked repository path exists.

### Task 2: Release automation and release documentation

**Files:**
- Create: `.github/workflows/release.yml`
- Create: `.github/release.yml`
- Create: `docs/RELEASING.md`
- Create: `CHANGELOG.md`

- [ ] Add semantic tag validation and Android version matching.
- [ ] Split RC and stable publication gates.
- [ ] Require signing secrets and build signed APK/AAB artifacts.
- [ ] Verify signatures, generate SHA-256 checksums, and attest artifacts.
- [ ] Publish generated release notes with the GitHub CLI.
- [ ] Document secrets, preflight checks, tag flow, and output assets.

### Task 3: Community health files

**Files:**
- Create: `CONTRIBUTING.md`
- Create: `SECURITY.md`
- Create: `.github/PULL_REQUEST_TEMPLATE.md`
- Create: `.github/ISSUE_TEMPLATE/bug_report.yml`
- Create: `.github/ISSUE_TEMPLATE/feature_request.yml`
- Create: `.github/ISSUE_TEMPLATE/config.yml`

- [ ] Standardize contribution and verification expectations.
- [ ] Add a fail-safe vulnerability-reporting policy without inventing a private address.
- [ ] Add structured issue forms that prohibit sensitive account data.
- [ ] Add a pull-request checklist for privacy, release, legal, and localization impact.

### Task 4: Repository validation and integration

**Files:** all files above

- [ ] Parse all added YAML files successfully.
- [ ] Confirm workflow action references are SHA-pinned.
- [ ] Confirm release workflow permissions are least-privilege for its operations.
- [ ] Open a PR against `main` and require fresh CI/security results before merge.
