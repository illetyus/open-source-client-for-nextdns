# Security Threat Model

This document describes the security assumptions and mitigations for
**Open Source Client for NextDNS**, an independent, unofficial Android client.

## Protected assets

- NextDNS API key.
- Profile configuration, allowlist, denylist and parental-control state.
- DNS query history and device metadata.
- Profile identifiers and account metadata.

## Trust boundaries

1. **Android app process** — trusted while the device and OS are not compromised.
2. **Android Keystore** — stores the non-exportable AES key used to protect the API key.
3. **App-private storage** — may hold encrypted credentials and cached configuration.
4. **NextDNS HTTPS endpoints** — authoritative source for remote configuration and logs.
5. **Public log-download URL** — used only after NextDNS returns it; the API key is not
   forwarded to this host.
6. **Clipboard / screen capture** — treated as disclosure surfaces and minimized.

## Implemented controls

### Credential at rest

- The NextDNS API key is encrypted with AES-256-GCM.
- The AES key is generated and retained by Android Keystore.
- Existing plaintext `saved_api_key` values are migrated on first read and removed.
- If secure credential storage fails, login is not reported as successful.
- The sensitive SharedPreferences file is excluded from cloud backup and device transfer.

### Network and logs

- Cleartext network traffic is disabled at manifest level.
- Production HTTP logging is disabled.
- `X-Api-Key` is registered as a redacted header for debug logging.
- Repository exception logging is debug-only.
- Log export requests a public URL with `redirect=0`, then downloads it without the API key.
- Profile-specific diagnostic hosts and copied setup commands use a shared normalized
  six-hex profile identifier. Invalid identifiers do not create diagnostic requests or
  expose setup instructions; this does not restrict generic repository profile records.

### DNS history

- DNS query history is memory-only.
- Legacy `saved_logs_*` disk caches are deleted during preferences initialization.
- Query history therefore does not survive a normal process/app restart.

### Credential disclosure UI

- The API key is masked by default.
- Revealed API keys automatically hide again after 10 seconds.
- Screen capture is blocked while the API key is revealed.
- Clipboard content is marked sensitive and a process-local callback attempts to clear it
  after 30 seconds if it still contains the copied API key. This is best-effort: process
  termination or Android clipboard-access restrictions may leave it present longer.

### Dependency and third-party network minimization

Unused Firebase AI, Firebase App Check, Google Services, Secrets Gradle, Room, Coil,
Credential Manager, Google ID, camera, location and permission-library entries are removed.

Rule and catalog screens may request decorative icons from `favicons.nextdns.io`. The
normalized hostname is included as reversible hexadecimal text in the HTTPS path; this
does not send the API key, and DNS log rows use a local icon. The endpoint is not part of
the documented NextDNS API and its retention practices have not been verified. The draft
privacy notice describes this transfer. Charts are rendered locally with Compose; the
main application does not use a JavaScript-enabled WebView.

## Residual risks

- The initial HTTPS log-download URL and HTTPS redirect destinations remain selected by
  the API response. Credentials are not attached, but local/private destination attempts
  are not excluded. Supported download/CDN origins require contract verification before
  introducing an origin allowlist; no API-response attacker-control path was established.

- A rooted or otherwise fully compromised device may inspect process memory or hook app calls.
- A user can deliberately disclose an API key to another app or person.
- Android Keystore protects key material but cannot make a compromised runtime trustworthy.
- NextDNS API availability and undocumented endpoints remain external dependencies.
- A public log-download URL should be treated as temporarily sensitive until it expires.
- Copied API-key cleanup depends on the app process and Android clipboard access; users
  should treat the 30-second clear as best-effort and clear the clip manually when needed.
- Screenshots taken before the secure-display flag is enabled are outside the app's control.

## Security invariants

- Never persist a plaintext API key.
- Never include the API key in logs, analytics or crash reporting.
- Never forward the API key to the public log-download host.
- Never persist DNS query history unless a future feature explicitly requires it and receives
  a separate privacy/security review.
- Server mutations are successful only after NextDNS accepts them and authoritative state can
  be re-read.
