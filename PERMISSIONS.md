# PERMISSIONS.md — Pip v1

Every permission declared in `app/src/main/AndroidManifest.xml`, why Pip
needs it, what still works without it, and when it is asked (V1-7, SEC-6).
Permissions are checked **at the moment of use**, never all at startup.
`scripts/check-permissions.sh` fails if a declared permission has no row here
(T-12).

## Declared permissions

| Permission | Function | If refused | When asked |
|---|---|---|---|
| `android.permission.INTERNET` | Brain calls over `https://` (V1-4) and the outbound `wss://` ACP connection (V1-6) | Granted at install, cannot be refused. Without a network: notebook, voice and screen context still work; the brain says "no network" (FR-4.5) | Never (install-time permission) |
| `android.permission.ACCESS_NETWORK_STATE` | Tell "no network" apart from server errors (FR-4.5); report network type for `device.status` | Granted at install | Never (install-time permission) |
| `android.permission.POST_NOTIFICATIONS` | The permanent notification S1 (FR-1.2), due reminders (FR-5.6), pending-task and orchestrator-message notifications | Pip refuses to run in the background and explains why on S3 (FR-1.4) | When the owner taps "Allumer Pip" on S2, or "Autoriser" on S3 |
| `android.permission.FOREGROUND_SERVICE` | Run `PipService` as a visible foreground service (V1-1) | Granted at install | Never (install-time permission) |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | The foreground-service type permission matching the type chosen in FR-1.5 (`specialUse`, see README) | Granted at install | Never (install-time permission) |
| `android.permission.RECORD_AUDIO` | Speech recognition while the owner holds the conversation open with Talk (FR-2.1). Pip never records or stores audio (FR-2.3) | Typing only (FR-2.5) | When the owner first taps "Parler" on S2, or "Autoriser" on S3 |

## Permission that protects a Pip component

| Permission | Function | If refused | When asked |
|---|---|---|---|
| `android.permission.BIND_ACCESSIBILITY_SERVICE` | Set as `android:permission` on `PipAccessibilityService`, so only the system can bind it. It is not requested by Pip; the owner turns the service on in system Accessibility settings | No screen context (FR-3.6); everything else works | When the owner taps "Ouvrir les réglages" on the Accessibility row of S3 |

## Package visibility (`<queries>`)

Not a permission. Pip declares two intent queries so Android lets it see the
engines it uses, and nothing else (no package listing, no
`QUERY_ALL_PACKAGES`):

- `android.speech.RecognitionService` — find a speech recognizer (FR-2.2).
- `android.intent.action.TTS_SERVICE` — find a text-to-speech engine (FR-2.4).

## Exported components (SEC-4 exceptions)

Every component is `android:exported="false"` except:

| Component | Why exported |
|---|---|
| `ui.MainActivity` | Launcher activity: the home screen must be able to start it. It accepts only the `MAIN`/`LAUNCHER` intent and reads no extras. |
| `screen.PipAccessibilityService` | The system binds accessibility services; it is protected by `BIND_ACCESSIBILITY_SERVICE`, which only the system holds. UNKNOWN: whether `exported="false"` would also work for a system-bound accessibility service — kept `true` per <https://developer.android.com/guide/topics/ui/accessibility/service> |

## Not requested in v1

Overlay (`SYSTEM_ALERT_WINDOW`), boot receiver (`RECEIVE_BOOT_COMPLETED`),
SMS, phone, call log, contacts, calendar, location, camera, package query
(`QUERY_ALL_PACKAGES`), usage stats, secure settings (SPEC section 5).

## Merged manifest

Third-party libraries (OkHttp, kotlinx-serialization) can add entries to the
merged manifest. UNKNOWN until the first build: the merged manifest at
`app/build/intermediates/merged_manifests/` is to be checked in Phase 7, and
any extra permission is either justified here or removed.
