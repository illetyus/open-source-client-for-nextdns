# Security Policy

## Supported versions

Until the first stable release is published, security fixes are applied to `main` and the current release-candidate line.

| Version | Security support |
| --- | --- |
| `main` | Yes |
| Current `vX.Y.Z-rc.N` pre-release | Yes |
| Older development snapshots | No |

After stable releases begin, this table will be updated to identify supported release branches explicitly.

## Reporting a vulnerability

Please do **not** open a public issue containing vulnerability details, API credentials, DNS logs, profile identifiers, private endpoints, signing material, or reproduction data that could expose another user's account.

Preferred reporting path:

1. Use GitHub's private vulnerability reporting / **Report a vulnerability** control for this repository when it is available.
2. Include the affected commit or version, impact, reproduction steps, and a minimal proof of concept that does not contain real user data.
3. If the private reporting control is unavailable, open a public issue containing only a request for a private security contact channel; do not include exploit details.

## Scope

Security reports are particularly useful for:

- API-key storage or disclosure;
- authentication or account-boundary failures;
- unintended destructive API operations;
- backup/restore or data-export exposure;
- insecure network behavior;
- sensitive logging or notification leakage;
- release signing, update, or supply-chain weaknesses;
- dependency vulnerabilities with a demonstrated path to the application.

The project is an independent client. Vulnerabilities in NextDNS-operated infrastructure should be reported to NextDNS through its own security process.

See [docs/SECURITY_THREAT_MODEL.md](docs/SECURITY_THREAT_MODEL.md) for the current application threat model.
