# Security policy

## Reporting a vulnerability

Please report security issues privately to the repository owner rather than in
a public issue. Include affected versions, reproduction steps and the expected
impact. Do not attach API keys, full device dumps or real journey logs.

## Secrets and client-side API keys

The repository must never contain `.env` files, keystores, provider keys or
tokens. Keys entered in the application are intended for personal installations
and are stored locally using Android facilities where supported.

An Android client controlled by its owner cannot provide absolute secrecy for a
long-lived provider key. Public or broadly distributed builds should use
short-lived credentials or a trusted backend that enforces authentication,
quotas and provider restrictions.

## Sideloading and updates

The built-in updater queries the latest GitHub Release of this repository,
downloads `A5Cockpit.apk` and verifies it against the published
`A5Cockpit.apk.sha256` checksum before handing it to Android's package
installer. It can also install a local APK chosen through Android's document
picker; the user is then responsible for selecting a trusted APK. In both cases
the application checks that the APK belongs to A5 Cockpit, and Android verifies
the signature and asks for confirmation before installing.

Release APKs published on GitHub Releases are signed with the project's stable
release key, which is kept outside the repository. Local builds without release
credentials use the machine's debug key and are not authenticated releases.

## Vehicle safety

This software is experimental and provides estimated values. It must not replace
the vehicle's certified instruments, warning lamps or safety systems. Do not
interact with diagnostic controls while driving.
