# Security Policy

## Reporting a vulnerability

Please report security issues privately, not as a public issue.

Use GitHub's [private vulnerability reporting][pvr] on this repository
(**Security** tab → *Report a vulnerability*). If that is unavailable to you,
email **`siegfredlorelle09@gmail.com`**.

Include what you did, what happened, and the device and Android version. A
proof of concept helps. Please do not include a real export file or real habit
data — see the note at the end.

This is a single-maintainer project, so expect an acknowledgement within about a
week rather than within hours.

[pvr]: https://docs.github.com/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability

## Supported versions

| Version | Supported |
|---|---|
| `main` | Yes |
| everything else | No |

There are no published releases yet. The app is at `0.1.0` / `versionCode 1`,
pre-1.0, and fixes land on `main` only.

## What the threat model actually is

Gawi is offline-first, and that shapes the attack surface more than anything
else in this document.

The app **declares no `INTERNET` permission**. It cannot open a socket, so there
is no remote attacker and no server to compromise. `ACCESS_NETWORK_STATE`
arrives transitively from WorkManager's manifest and is stripped with
`tools:node="remove"`. The full requested set is `WAKE_LOCK`,
`RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, a Glance-generated
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, and `POST_NOTIFICATIONS`. None can
move data off the device. This is enforced by a test, not by convention:
`app/src/test/kotlin/com/gawi/app/ManifestPermissionTest.kt` reads the *merged*
manifest, so a permission arriving through a library's manifest fails the build.

Android Auto Backup is off (`allowBackup="false"`). That closes an egress path
that needs no network permission at all — the OS copying app data to the user's
Google account — and it has a cost worth knowing: there is no OS-mediated
restore, so **export is the only recovery path**.

## In scope

- **The export path.** `ContentResolverEventArchive` and
  `ContentResolverCompletionCsvArchive` write to a document the user picks
  through the Storage Access Framework. This is the one place user data
  deliberately leaves the app sandbox.
- **CSV injection in exports.** `CompletionCsv` guards formula-leading sigils
  because a spreadsheet may evaluate a cell on open. If you find a reader that
  is exploitable through an exported file, that is in scope.
- **Import.** `ExportReader` and `EventLogCodec` parse a file the user chose.
  Malformed or hostile input should be rejected, never trusted.
- **The widget.** `TodayWidget` produces `RemoteViews` inflated by a launcher in
  another process, and `ToggleHabitAction` writes on tap.
- **Scheduled workers.** `ReminderWorker` and `RolloverWorker` wake and read the
  database.
- Anything that makes the app request a network permission, or that moves data
  off the device by any route.

## Out of scope

- **Exports are plaintext, by design.** JSON and CSV, unencrypted. Data
  ownership is the point (PRD §2), and encrypting exports would trade it for a
  key the user has to keep. An unencrypted export is not a vulnerability; a
  *reader* exploitable through one is.
- **An attacker with physical access to an unlocked device.** Local storage is
  protected by the Android sandbox and the device lock, and nothing more.
- **A rooted or compromised device.** The sandbox is the boundary; below it
  there is nothing to defend.
- Findings against the LAN and cloud sync described in PRD §5 phases 2 and 3.
  Neither is built.

## Please do not send real habit data

An export file is a complete record of what someone does every day. It is the
private thing this app exists to protect. Reproduce with invented habits where
you can, and redact where you cannot.
