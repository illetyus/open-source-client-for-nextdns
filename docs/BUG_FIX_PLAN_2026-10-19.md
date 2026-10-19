# Eight-defect correction plan

Approved scope: NDNS-012 through NDNS-019 from HATA_AVI_TR_2026-10-19.md.
Baseline: 4812323; audit head: 1d740cf. Historical failing evidence is retained.

1. Compare diagnostic identity against the selected profile fingerprint; preserve unknown/error states.
2. Display and copy validated API IPv6 endpoints. Do not invent missing service endpoints.
3. Normalize domain rules at the repository boundary and use the same validation in both forms.
4. Clear only the successfully submitted form text; retain failed, cancelled and subsequently edited input.
5. Commit complete analytics snapshots only, and reject superseded filter responses and busy-state updates.
6. Apply authoritative log refreshes without retaining remotely removed history or losing intervening stream entries.
7. Supersede in-flight authentication when entering guest mode.
8. Extend required regressions, run full tests/build/lint/security, review, update the defect ledger and integrate through PR #25 after green CI.

Ruling: reuse the clean, dedicated audit/bug-hunt-2026-10-19 development branch and PR #25. The user authorized fixes on this existing development stream; main is not edited directly.
Ruling: user approval covers these corrections and normal successful-CI integration; no repeated approval checkpoint is introduced.
Shared interfaces: domain normalization precedes asynchronous form completion; repository analytics request ownership and ViewModel loading ownership must agree; diagnostic banner and diagnostic toast use the same selected fingerprint.
Verification: historical Linux and Windows audit runs reproduce seven failures; add endpoint/domain and boundary cases, observe red, then green. Full suite remains mandatory.
Endpoint decision: when setup.ipv6 is absent or invalid, show unavailable and disable copying instead of deriving undocumented service addresses. Cost: offline setup cannot supply an IPv6 endpoint until a valid service response arrives.
Sources: [NextDNS API error and analytics contracts](https://nextdns.github.io/api/); [Android IDN conversion and STD3 validation](https://developer.android.com/reference/java/net/IDN). The existing wildcard input remains supported; Unicode is normalized to ASCII at the repository boundary. Successful HTTP status alone does not imply an accepted mutation.
Release constraints: preserve independent branding, technical identifiers, legal DRAFT status and signing identity. JVM/CI success does not imply new physical-device or public-release acceptance.
