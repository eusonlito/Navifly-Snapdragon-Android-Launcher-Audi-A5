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

The built-in updater opens Android's document picker and package installer. The
user is responsible for selecting a trusted APK and reviewing Android's install
confirmation. Release artifacts produced by the public build use a debug key
for local testing and are not authenticated public releases.

## Vehicle safety

This software is experimental and provides estimated values. It must not replace
the vehicle's certified instruments, warning lamps or safety systems. Do not
interact with diagnostic controls while driving.
