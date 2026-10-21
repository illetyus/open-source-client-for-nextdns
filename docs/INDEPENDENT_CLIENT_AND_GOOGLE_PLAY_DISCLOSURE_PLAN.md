# Phase 8/9 — Independent NextDNS client positioning and Google Play/API disclosure matrix

**Date:** 2026-10-08  
**State:** Product/legal copy and release-submission PLAN, not final legal advice, Play Console submission, SDK audit, or verified real-device network behavior.  
**Scope:** Phase 8 source baseline `phase8-compliance`. Final user-facing app name and original icon are still Phase 9 decisions.

## 1. Accurate allocation of responsibility

The product is an **independent, unofficial Android account-management client**. It is **not** a DNS resolver, VPN, NextDNS subscription seller, NextDNS official application or NextDNS-hosted DNS service. It uses the NextDNS API with credentials provided by the user. We must not claim authorization or endorsement that has not been granted.

**NextDNS-controlled scope:** NextDNS's DNS service and remote infrastructure, account and subscription services, API availability and server-side feature implementation, DNS logs and remote retention according to the NextDNS account settings, server-level processing. These are described as operated by NextDNS and subject to NextDNS terms/privacy policy.

**Application developer-controlled scope:** the app's own code, secure handling and local storage of API credentials, construction/authentication of requests, changes sent to NextDNS on the user's behalf, local profiles/cache and notifications, settings export, errors, app updates and Android permissions, app's user notices and Play declarations. A disclaimer **does not eliminate** the developer's legally applicable privacy/security/consumer obligations or provide protection against negligent app behavior. Never assert the app developer has *no liability* for any data processing.

**No developer-controlled account backend is intended** in initial release. This is an architectural claim to verify with final AAB/APK, effective manifest, runtime dependencies and actual network trace before publication; do not assert that nothing leaves the device (requests go to NextDNS; downloads may use NextDNS-provided HTTPS export hosts).

**No extra owner-only rule:** The product owner already chose to require compliance with NextDNS Terms and applicable law, **not** to restrict use to only accounts legally owned by the device operator. Never reinsert `you must use your own NextDNS account` as a product-specific rule. Unauthorized access is not allowed under applicable law/NextDNS rules.

## 2. Proposed short Play listing/first-run explanatory statement

> **Independent client (unofficial).** This application is an independently developed Android client for managing NextDNS accounts through the NextDNS API. It is not developed, operated, sponsored, endorsed or officially supported by NextDNS. NextDNS independently operates its DNS infrastructure, account and subscription services, and API. Changes, outages, restrictions or discontinuation of the NextDNS API may cause connection errors, compatibility issues or unavailable features. The application developer does not accept responsibility for connection errors, compatibility issues or loss of functionality resulting from changes, interruptions, restrictions or discontinuation of the NextDNS API or services. Details of data processing are provided in the Privacy Policy.

**Do not publish verbatim until the final identity, functionality and privacy statements have been verified.** Place a compact version visibly in the Play listing, login/onboarding and About page. Longer, correctly localized provisions belong in Terms and the Privacy Policy. Avoid deceptive official branding/name/icon/screenshot even if a disclaimer is present.

## 3. Longer legal concept (draft, English source)

> **Independent and Unofficial NextDNS Client.** This application is an independently developed third-party client providing access to selected NextDNS account-management features through the NextDNS API. It is not developed, operated, sponsored, endorsed or officially supported by NextDNS. NextDNS independently operates and controls its DNS infrastructure, servers, accounts, subscriptions, API availability and server-side data processing. This app is dependent on NextDNS's separate service and API, which may be modified, restricted, interrupted or discontinued without notice. Such events, including changes to endpoints, authentication requirements, data formats or usage limits, may cause connection errors, compatibility problems, incorrect or outdated displays or loss of functionality. **The application developer does not accept responsibility for connection errors, compatibility issues, or loss of functionality resulting from changes, interruptions, restrictions, or discontinuation of the NextDNS API or services.** Users must comply with applicable law and NextDNS's own service terms. This application does not operate DNS infrastructure or provide NextDNS services directly.

Five-language Terms translations (tr/en/de/fr/es) must match one legal revision, be reviewed for consistency, and not create a misleading jurisdiction waiver. Privacy Policy is currently planned in English; separately assess whether a Turkish KVKK aydınlatma text/other local-language notice is required and publish it independently from consent when appropriate.

## 4. What Google integrations are actually present? (source-level snapshot)

Checked `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/example/data/api/NextDnsApiService.kt`, privacy audit, dependency review, and current release-negative scan:

- **Google Play publishing** and use of Google-created **AndroidX/Jetpack** libraries and **KSP build plugin** are **not**, by themselves, integrations with Google Sign-In, Google Cloud APIs or Firebase analytics.
- The 8 October source snapshot directly used Retrofit/OkHttp for `https://api.nextdns.io/`, `https://test.nextdns.io/`, `https://link-ip.nextdns.io/` and NextDNS-provided log-download URLs. The 21 October candidate removes its direct `link-ip.nextdns.io` request because the public REST documentation does not define that operation; the setup screen opens `https://my.nextdns.io/` in the user's browser for linked-IP management.
- DTO field `googleSafeBrowsing` and related NextDNS safe search / YouTube restrictions are **configuration values exposed by NextDNS**; the field's name alone does **not** show the client integrates Google Safe Browsing API or transmits user data directly to Google.
- Direct runtime dependencies **do not list** Firebase Analytics, Firebase Crashlytics, AdMob, Google Billing, Google Sign-In, Google Maps or Google Cloud API SDKs in this snapshot; no separate Google service integration is a product requirement for Play publication.
- Android permissions in the manifest: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`. These are Android platform permissions, not special permission to use a Google API.
- **Limits:** direct dependencies and selected source files are not exhaustive proof. Resolve `releaseRuntimeClasspath`, inspect merged manifest/binary/AAB, and capture actual traffic. Current negative gate is limited to a short list of Maven groups.

**Future integrations (not approved):** Firebase/Crashlytics, Google Analytics, AdMob, Maps, Sign-In/OAuth, Play Billing, Google Cloud/AI APIs, etc. require **separate** product decision, API/vendor terms & key management, proper data flow/legal assessment, additional Data Safety declarations and Privacy Policy changes if relevant. Publishing on Google Play does not mean these APIs will be bundled automatically.

## 5. Google Play — what to actually submit or describe

| Issue | Treatment |
|---|---|
| Independently operated third-party client | Truthfully identify the developer and app's purpose in listing and inside app; no false NextDNS endorsement, official logos, or impersonation. Trademark rights and API/service terms remain separate legal questions, not cured solely by disclaimer. |
| Which external services/API endpoints are used? | No generally applicable Play form that demands a list of every REST API merely because the app is published. Describe **data processing/transfers, accessed permissions, relevant SDKs** accurately under the Privacy Policy and Data safety form. Google may request more information in policy review. |
| Privacy Policy | Public, working non-geofenced HTTPS **web page**, also linked in app; accurately describe app flows, developer contact, NextDNS as separate service and log-download behavior. |
| Data safety | **Mandatory for published/closed/open test tracks** (internal-only exception applies); `collection` includes sending information off the phone **to NextDNS**, even if there is no app-developer server. Do not automatically declare `no data collected`. Classification of `sharing` requires case-by-case assessment; Google describes a user-initiated action exception for *sharing*, not a blanket collection exemption. |
| App access | Because some real functionality needs a NextDNS API credential, provide Play reviewers with access guidance and, if required, dedicated safe/working test NextDNS credentials that permit review beyond demo mode. Do not disclose credentials in public GitHub or store listing. |
| App/account deletion | Local Logout deletes local account data; it does **not** delete a remote NextDNS account or revoke API keys. Account-creation/deletion declaration depends on whether the app actually lets users create an account, including routing to an external account-creation flow; reassess final UX before filing. |
| Ads, billing, other developer declarations | Initial public release intends no ads or in-app purchases; verify compiled AAB and answer Play declarations accurately, including target audience/content rating and any required notification explanation. |
| Google APIs | No standalone blanket approval/disclosure that says `We use Google API` for using Android SDK or for uploading to Play. If a real Google SDK/API gets added, check that service's access/credential and data-use requirements, then update Play disclosures where applicable. |

## 6. Release checks / blockers

- [ ] Final app name and independent icon avoid suggesting NextDNS partnership.
- [ ] Concise unofficial-client notice added to Play short/full description, onboarding/login and About page as appropriate.
- [ ] Five-language Terms clauses align with approved account access and scope wording; no unenforceable total-liability waiver.
- [ ] Actual developer contact and public privacy links are provided.
- [ ] Turkish KVKK notice needs/identity/roles and cross-border transfer review completed for relevant Turkish data handling.
- [ ] Direct/indirect SDK dependencies plus actual AAB manifest/network behavior audited; identify any third-party APIs and hosts.
- [ ] Data Safety category matrix is completed **from measured flows** and legally reviewed before publication.
- [ ] App access reviewer credentials/test account provisioned safely if needed; accounts not made public.
- [ ] User-facing deletion descriptions accurately distinguish local logout, exported files and NextDNS remote deletion.

## 7. Authoritative sources (checked 2026-10-08)

- Google Play Impersonation: https://support.google.com/googleplay/android-developer/answer/9888374?hl=en
- Google Play User Data + SDK obligations: https://support.google.com/googleplay/android-developer/answer/10144311?hl=en
- Google Play Data safety definitions and declarations: https://support.google.com/googleplay/android-developer/answer/10787469?hl=en
- Google Play Account deletion: https://support.google.com/googleplay/android-developer/answer/13327111?hl=en
- Play SDK requirements: https://support.google.com/googleplay/android-developer/answer/13323374?hl=en
- NextDNS API documentation: https://nextdns.github.io/api/
- NextDNS Privacy: https://nextdns.io/privacy

**Draft only:** Do not mistake this document for legal clearance, NextDNS permission or Google Play approval.

## 8. Deep literature review and updated release gates (2026-10-08)

The detailed legal research and reprioritized release gates are documented in [LEGAL_RISK_LITERATURE_REVIEW_AND_RELEASE_PLAN_2026-10-08.md](LEGAL_RISK_LITERATURE_REVIEW_AND_RELEASE_PLAN_2026-10-08.md). Key changes: identify the NextDNS Help Center's Forumbee Terms as **not the general DNS service agreement**, require authoritative API/trademark scope review, forbid relying on disclaimer to excuse the app's own bugs, prioritize destructive API safeguards, Google Play Data Safety collection to NextDNS, and issue-by-issue KVKK controller/cross-border mapping.

## 9. External NextDNS API change disclaimer — added at user request (2026-10-08)

**Agreed scope:** Onboarding/Play copy may warn that NextDNS API changes, outages, restrictions, altered payloads/authentication rules or service withdrawal can cause compatibility problems, bugs/errors and temporary/permanent loss of access. Use the agreed concise wording: **'The application developer does not accept responsibility for connection errors, compatibility issues, or loss of functionality resulting from changes, interruptions, restrictions, or discontinuation of the NextDNS API or services.'** Do **not** represent ordinary developer-caused software defects, key leakage or incorrect requests as NextDNS API changes. Avoid claiming that users waive all legal rights. The agreed sentence above is the English master for five-language Terms translation and may be used with a shorter factual warning in listing/onboarding; keep mandatory legal review separate from user-facing wording.

**Technical companion to the disclaimer:** Test unavailable, malformed and altered API responses (including HTTP 401/403/404/429/5xx, connection timeout, field removal/type changes). Fail safely, never show stale data as live, do not claim success without authoritative server confirmation, and prevent account/profile deletion or configuration mutations from targeting the wrong record after an API change. The text is a notice, not a substitute for these defenses.


## Selected application title (8 October 2026)

The agreed first-release title is **Open Source Client for NextDNS**, and the repository slug is `illetyus/open-source-client-for-nextdns`. The Android label and login screen use this title. The provider NextDNS remains distinct. This resolves product name selection, **not** NextDNS trademark clearance, launcher-asset ownership, store listing review or final Play approval.
