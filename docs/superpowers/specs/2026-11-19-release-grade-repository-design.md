# Release-grade repository design

## Intent

Prepare the repository for its first GitHub release so that the public surface reads like a maintained Android product rather than an internal development ledger, while preserving the project's existing security, legal, and release safeguards.

## Goals

- Make the root README concise, visual, product-oriented, and entirely English.
- Use project-authored repository artwork for the durable showcase and keep real CI-generated UI screenshots as test evidence rather than hand-edited marketing mockups.
- Add conventional open-source community health files and issue/PR intake.
- Add a tag-driven signed release workflow with checksums and provenance attestations.
- Make release candidates possible while keeping stable publication fail-closed until final legal/publication metadata is complete.
- Keep deep historical/compliance evidence available without placing it in the README path of every visitor.

## Non-goals

- Publish to Google Play in this change.
- Invent final publisher/legal/contact details.
- Remove or weaken existing DRAFT/legal gates.
- Change application behavior or NextDNS API logic.
- Replace project-authored visual identity with NextDNS branding.

## Constraints

- Public-facing repository language is English.
- Existing legal placeholders remain unresolved until the Play/publication stage.
- Release binaries must be signed; unsigned CI outputs remain review artifacts only.
- Workflow dependencies should remain SHA-pinned and use least-privilege permissions.
- The repository must continue to describe itself as independent and unofficial.

## Approaches considered

### A. README-only polish

Fastest, but leaves release mechanics, security reporting, contributor workflow, and release notes ad hoc. Rejected as insufficient for a first public release.

### B. Release-grade repository package — selected

Rework the public README, add a versioned project-authored hero, keep CI-derived screenshots as test evidence, add community health files, release documentation, curated release notes, and a dedicated signed tag workflow. This is the best balance between professional presentation and existing release controls.

### C. Immediate stable/Play publication automation

Would automate a stable release now, but conflicts with known publisher/legal placeholders and the repository's fail-closed publication rules. Rejected until the final publication metadata is available.

## Design

The root README becomes a product landing page: identity, independence notice, capabilities, real screenshots, release status, security/privacy model, build instructions, structure, contribution, integrity, and licensing. Detailed phase history moves behind a documentation index.

A new `Release` workflow responds to semantic version tags. Pre-release tags (`-rc.N`) run draft-safe checks and publish GitHub pre-releases. Stable tags run final publication/legal gates and fail if draft markers remain. Both paths require signing secrets, verify APK/AAB signatures, generate checksums, add GitHub artifact attestations, upload workflow artifacts, and create categorized GitHub release notes.

Community health files standardize bug reports, feature requests, pull requests, contribution expectations, and security disclosure. No private contact detail is invented.
