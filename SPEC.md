# SPEC.md — Pip v1

The functional and security specification for Pip, a personal resident
Android agent. **This document is the only input for implementation
(Phase B).** It states *what* Pip must do. It never describes how any other
application does it, and it contains no code, strings, assets, or structure
taken from anywhere else.

- Owner: Michael Boyer. Devices in scope: the owner's own Android devices.
- Status: v1, personal debug builds only.
- Words used: **MUST** = required for v1; **SHOULD** = expected unless a
  documented reason prevents it; **MAY** = optional.
- `UNKNOWN` marks a point this spec does not settle; Phase B resolves it
  against official Android documentation and records the URL.

## 1. Product summary

Pip is a small companion that lives on the owner's phone. The owner talks to
it, it answers out loud, it can look at the current screen **when asked**,
it keeps a private encrypted notebook, and it can receive tasks from the
owner's own orchestrator — each of which waits for the owner's yes on the
device. Pip is always visible while it runs, and it can always be stopped
in one tap.

Guiding principles:

1. **Visible.** If Pip is running, its notification is showing. If the
   notification is gone, Pip is stopped.
2. **Asked, not assumed.** Screen context, notebook excerpts, and remote
   tasks each need an explicit owner action, every time.
3. **Local by default.** Nothing leaves the device unless this spec names
   the destination and the trigger.
4. **Honest failure.** Pip says what went wrong in plain words; it never
   disguises a missing setting as a network error.

## 2. Scope

### 2.1 v1 — build

| ID | Function |
|---|---|
| V1-1 | Resident foreground service with permanent notification and Stop action |
| V1-2 | Voice in and out through Android speech recognition and text-to-speech |
| V1-3 | Read-only screen context through an accessibility service |
| V1-4 | Swappable brain: OpenAI-compatible endpoint, owner-supplied key |
| V1-5 | Encrypted on-device notebook (notes, promises, simple reminders) |
| V1-6 | Visible ACP client (section 7) |
| V1-7 | `PERMISSIONS.md` mapping every declared permission |

### 2.2 Parked for v2 — do not build

- Multi-device features and pairing devices with each other.
- Meeting recorder.
- Camera used as an "eye" (object look, code scanning).
- Automatic actions on other applications' screens (taps, typing, swipes).
- Robots or any physical accessory.

### 2.3 Observed elsewhere, deliberately not retained for v1

Listed so Phase B does not reintroduce them by accident: an always-listening
offline wake word; a floating on-screen avatar drawn over other apps; any
local network server; reading or sending SMS; reading the call log or
contacts; placing calls; calendar access; location; step counting; listing
or launching installed apps; reading other apps' notifications; screenshots
or screen recording; changing system input settings; a remote "hub" mode
that hands the whole conversation to a server.

## 3. Surfaces

All visible text is French first with an English alternative, in Pip's own
voice (defined in `BRAND.md`). Descriptions below are functional only.

| ID | Surface | Contents | Owner actions |
|---|---|---|---|
| S1 | **Persistent notification** | Pip's state (ready, listening, thinking, speaking, waiting for approval, stopped-by-error); ACP connection state; title of the last ACP task and its outcome | **Stop** (always present); open Pip; when a task is pending: open the approval screen |
| S2 | **Home** | Large talk button; last exchange (owner's words, Pip's answer); "look at my screen" toggle for the next turn; indicators: brain configured yes/no, accessibility on/off, ACP connected/off | Talk; type instead of talking; attach screen context to the next turn; open Settings, Notebook, Tasks |
| S3 | **Setup & permissions** | One row per capability: what it gives, what is lost if refused, current state | Grant or open the matching system screen; skip |
| S4 | **Settings — Brain** | Endpoint base URL (must be `https://`), model name, key field (masked, write-only: shows "set" / "not set", never the key) | Save; test connection; clear key |
| S5 | **Settings — ACP** | Orchestrator address (must be on the build allowlist), pairing state, key age | Pair; re-pair (rotates); revoke; connect / disconnect |
| S6 | **Notebook** | List of entries by kind (note, promise, reminder) with date | Add; edit; delete one; **erase all** (with confirmation) |
| S7 | **Task approval** | One pending ACP task: intent name, plain-language description of exactly what will happen, arguments shown in full, time left before expiry | **Approve** or **Refuse** — one task at a time, no "always" option |
| S8 | **Task journal** | Chronological list of ACP events with timestamp and outcome | View; clear journal (with confirmation) |
| S9 | **Notebook-excerpt approval** | The exact excerpt Pip proposes to send to the brain for this turn | **Send this once** or **Don't send** |

## 4. Functional requirements

### 4.1 V1-1 — Resident service

- FR-1.1 Pip MUST run as a foreground service started only by an explicit
  owner action (opening Pip and turning it on). It MUST NOT start itself at
  boot in v1.
- FR-1.2 While the service runs, notification S1 MUST be shown, marked
  ongoing, and carry a **Stop** action at all times, in every state.
- FR-1.3 Stop MUST end the service, close the ACP connection, cancel any
  in-flight brain request and speech, discard pending ACP tasks as
  "cancelled by stop" (journaled), and remove the notification.
- FR-1.4 If the notification cannot be shown (notification permission
  refused), Pip MUST NOT run in the background; it explains why on S3.
- FR-1.5 Foreground-service type for Android 14+: `UNKNOWN` — Phase B picks
  the type that matches the running work and documents the reason, per
  <https://developer.android.com/develop/background-work/services/fg-service-types>.

### 4.2 V1-2 — Voice

- FR-2.1 Speech recognition MUST start only when the owner presses talk (no
  always-on listening). A visible "listening" state MUST show while the mic
  is open, on S2 and S1.
- FR-2.2 Pip MUST prefer an **on-device** recognizer. If none is available,
  Pip MUST say so and use the system default recognizer **only after the
  owner opts in once in Settings**, with the warning that the system
  provider may process the audio off the device. Availability check and API
  level: `UNKNOWN` — see
  <https://developer.android.com/reference/android/speech/SpeechRecognizer>.
- FR-2.3 Pip itself MUST NOT record, store, or upload audio. Only the
  recognized text is used.
- FR-2.4 Answers MUST be spoken with the Android system text-to-speech
  engine, French by default, English when the owner speaks English. The
  owner can interrupt speech with one tap.
- FR-2.5 Typing MUST work as an alternative to speaking.

### 4.3 V1-3 — Screen context (read-only)

- FR-3.1 An accessibility service MUST read the window content of the
  current foreground app **only when the owner asks** for it on that turn
  (toggle on S2, or a spoken request recognized locally).
- FR-3.2 It MUST NOT perform any action: no gestures, no clicks, no text
  entry, no global actions, no screenshots. Its configuration MUST request
  only what reading window content needs.
- FR-3.3 The snapshot MUST be turned into a compact text description in the
  **Pip Screen Snapshot** format (section 6.2).
- FR-3.4 Password fields MUST be excluded entirely. Pip's own windows MUST
  be excluded.
- FR-3.5 The owner MUST be able to see the snapshot text that was attached
  to a turn.
- FR-3.6 If the accessibility service is off, the toggle is disabled and S2
  says how to turn it on; everything else keeps working.

### 4.4 V1-4 — Brain

- FR-4.1 Pip MUST call an OpenAI-compatible chat-completions endpoint at the
  owner-configured base URL, with the owner-configured model and key.
- FR-4.2 The base URL MUST use `https://`; any other scheme is refused at
  input.
- FR-4.3 The key MUST be entered on the device and stored only in encrypted
  storage (SEC-3). It MUST never appear on screen after saving, in logs, in
  crash reports, in the notebook, or in ACP traffic.
- FR-4.4 If the key is empty, Pip MUST say plainly that no brain key is set
  and point to S4. It MUST NOT attempt the request.
- FR-4.5 Errors MUST be told apart and worded honestly: no network; server
  refused the key; server error; timeout; malformed answer.
- FR-4.6 What a turn sends: Pip's fixed instructions (written for Pip in
  Phase B), the recent conversation of the current session, the owner's new
  words, and — only if attached for this turn — the screen snapshot and/or
  an approved notebook excerpt. Nothing else.
- FR-4.7 v1 brain answers are text only. The brain cannot trigger device
  actions in v1 (no tool calling to the device).

### 4.5 V1-5 — Notebook

- FR-5.1 Entries: `note`, `promise`, `reminder` (a reminder has an optional
  due time). Each entry has an id, kind, text, created time, optional due
  time, done flag.
- FR-5.2 Storage MUST be encrypted with AES-GCM using a key held in Android
  Keystore (SEC-3). Each write uses a fresh random IV.
- FR-5.3 The owner can view, add, edit, delete one, and erase all (S6).
  Erase all deletes the data and the Keystore key.
- FR-5.4 **The notebook never leaves the device by default.** No backup, no
  sync, no export in v1.
- FR-5.5 **Notebook excerpts to the brain require per-turn approval.** When
  a turn would benefit from notebook content, Pip shows the exact excerpt on
  S9. Only "Send this once" sends it, for that turn only. Refusal sends
  nothing and the turn continues without it. There is no "always send".
- FR-5.6 Due reminders produce a notification while Pip runs. If Pip was
  stopped when one fell due, it is shown as overdue the next time Pip
  starts. (No boot receiver in v1.)

### 4.6 V1-6 — ACP client

Defined in section 7. It lives inside the V1-1 service and is shown on S1.

### 4.7 V1-7 — Permissions document

`PERMISSIONS.md` MUST list every permission in the merged manifest, each with
the function, what degrades if refused, and when it is asked. Permissions are
checked **at the moment of use**, never all at startup.

## 5. Permissions (v1 target set)

| Permission | Function | If refused |
|---|---|---|
| `INTERNET` | brain calls, ACP connection | no brain, no ACP; notebook and voice still work |
| `ACCESS_NETWORK_STATE` | tell "no network" apart from server errors (FR-4.5) | errors are less precise |
| `POST_NOTIFICATIONS` | the permanent notification (FR-1.2), due reminders | Pip refuses to run in the background (FR-1.4) |
| `FOREGROUND_SERVICE` (+ the type permission chosen in FR-1.5) | resident service | Pip cannot stay resident |
| `RECORD_AUDIO` | speech recognition | typing only |
| `BIND_ACCESSIBILITY_SERVICE` (protects Pip's service; granted by the owner in system settings) | screen context | no screen context |

Not requested in v1: overlay (`SYSTEM_ALERT_WINDOW`) — no v1 function draws
over other apps; boot receiver; any SMS, phone, contacts, calendar,
location, camera, package-query, usage-stats or secure-settings permission.

## 6. Data formats (defined for Pip)

### 6.1 Notebook record (plaintext before encryption)

JSON object: `id` (UUID string), `kind` (`note` | `promise` | `reminder`),
`text` (string), `created_at` (UTC ISO-8601), `due_at` (UTC ISO-8601 or
null), `done` (boolean). The stored file is one encrypted blob holding a JSON
array of records, prefixed by its IV.

### 6.2 Pip Screen Snapshot (text)

Plain text, one header line then one line per visible node, depth-indented
by two spaces:

```
[pip-snapshot v1] app=<package> time=<UTC ISO-8601> nodes=<n> truncated=<yes|no>
<role> "<visible text or description>" [state flags]
```

- `role`: one of `text`, `button`, `field`, `image`, `list`, `item`,
  `toggle`, `other`.
- State flags as needed: `checked`, `selected`, `disabled`, `focused`.
- Empty, invisible, and password nodes are skipped.
- Hard cap: Phase B sets a node and character limit; when hit,
  `truncated=yes`.

### 6.3 ACP envelope

UTF-8 JSON object, one per WebSocket text frame:

| Field | Type | Meaning |
|---|---|---|
| `v` | integer | protocol version, `1` |
| `agent_id` | string | MUST equal `pip` |
| `msg_id` | string | unique id (UUID) |
| `ts` | integer | sender time, Unix epoch milliseconds |
| `nonce` | string | 16 random bytes, base64url |
| `type` | string | `hello`, `task`, `decision`, `result`, `status`, `error` |
| `body` | object | type-specific content |
| `sig` | string | HMAC-SHA256, base64url |

Signature input: the envelope without `sig`, serialized with keys sorted
lexicographically at every level, no insignificant whitespace, UTF-8. Key:
the 32-byte pairing secret (7.4).

`task` body: `task_id` (string), `intent` (string, from 7.5), `args`
(object, validated against that intent's schema), `summary` (string,
orchestrator's wording — shown, but Pip also renders its own description).

`decision` body (device → orchestrator): `task_id`, `outcome` (`approved` |
`refused` | `expired` | `rejected` | `cancelled`), `reason` (string).

`result` body: `task_id`, `ok` (boolean), `data` (object, intent-specific).

## 7. ACP doctrine — owner's devices only

Implemented exactly as stated; nothing added.

### 7.1 Visible at all times

- ACP-1 The ACP client runs inside the V1-1 foreground service. S1 shows
  connection state, the last task, and Stop.

### 7.2 Outbound only

- ACP-2 The device opens the connection to the owner's orchestrator over
  `wss://` only.
- ACP-3 The orchestrator host MUST be on an allowlist fixed at build time
  (build configuration plus network security configuration). Any other host
  is refused before connecting.
- ACP-4 Pip opens **no** listening port for ACP or anything else.

### 7.3 Per-task approval

- ACP-5 Every incoming task enters `pending` and is shown on S7 with its
  intent and full arguments.
- ACP-6 It runs only after the owner taps Approve on the device.
- ACP-7 Refuse → nothing runs.
- ACP-8 No answer within **5 minutes** of receipt → `expired`; nothing runs.
- ACP-9 No global approval, no "always allow", no batch approval. One task,
  one decision.
- ACP-10 Every decision is sent back to the orchestrator as a `decision`
  envelope.

### 7.4 Integrity

- ACP-11 Every envelope, in both directions, is signed with HMAC-SHA256 over
  the canonical form (6.3).
- ACP-12 Incoming envelopes are rejected without execution when: the
  signature does not verify (constant-time compare); `agent_id` is not
  `pip`; `ts` is outside the freshness window; the `nonce` or `msg_id` was
  already seen within that window; `v` is unsupported; the JSON is
  malformed. Freshness window: **120 seconds** either way (confirmed by
  the owner, Q-3). Each rejection is journaled.
- ACP-13 Pairing: on the device, the owner taps Pair; Pip generates a
  32-byte random secret, shows it once for the owner to enter into the
  orchestrator, and stores it encrypted (SEC-3). The screen showing it MUST
  block screenshots of that view.
- ACP-14 Re-pairing generates a new secret and destroys the old one
  (rotation on every re-pairing). Revoke destroys the secret and
  disconnects; afterwards every envelope is rejected until a new pairing.

### 7.5 Closed list of intents

The only intents that exist, declared in code:

| Intent | Effect when approved | `args` |
|---|---|---|
| `device.status` | reply with Pip version, battery level, charging yes/no, network type, service state | none |
| `snapshot.last` | reply with the last Pip Screen Snapshot the **owner** captured on the device (never a new capture) | none |
| `message.show` | show a message on S2 and in a notification; may be spoken | `text` (≤ 500 chars) |
| `reminder.create` | add a `reminder` entry to the notebook | `text` (≤ 500 chars), `due_at` (optional) |

Unknown intents are rejected. Explicitly forbidden even with approval:
shell or arbitrary remote execution; any free-form Android `Intent`; any
free-form URL to open or fetch; camera, screen, or microphone capture
triggered remotely without the owner's explicit consent for that task;
sending SMS or placing calls.

### 7.6 Perimeter

- ACP-15 Owner's own devices only. No hidden or "undetectable" mode, no
  control of third-party devices, no hosting of devices for other people.

### 7.7 Local journal

- ACP-16 Every task event — received, pending, approved, refused, expired,
  rejected (with reason), executed, failed, cancelled — is written to an
  on-device journal with a UTC timestamp and shown on S8. Nothing is silent.
- ACP-17 The journal never stores the pairing secret or the brain key.

## 8. Security requirements

- SEC-1 **No cleartext.** The network security configuration disallows
  cleartext globally with no exceptions; no HTTP fallback anywhere.
- SEC-2 **No listening sockets.** If a future local channel is ever added,
  it binds to loopback only and checks the pairing secret before any
  command.
- SEC-3 **Secrets at rest.** The brain key and the ACP pairing secret live
  only in AES-GCM encrypted storage whose key is in Android Keystore. Never
  in plain preferences, files, logs, or the repository.
- SEC-4 **Manifest.** `android:allowBackup="false"`; every component
  `exported="false"` unless Android requires otherwise (the accessibility
  service, protected by its bind permission; the launcher activity) — each
  exception documented in `PERMISSIONS.md`.
- SEC-5 **Builds.** Release: minification and resource shrinking on. Debug:
  clearly separate (distinct application id suffix and label). No debug-only
  component in release.
- SEC-6 **Permissions minimal.** Every manifest permission maps to a line in
  `PERMISSIONS.md`, otherwise it is removed.
- SEC-7 **No secrets in the tree.** The secrets gate runs before every
  commit; `gitleaks` runs too when available.
- SEC-8 **Logs.** No key, secret, notebook text, or snapshot text in logcat.
- SEC-9 **Inputs.** All ACP `args` and brain responses are treated as
  untrusted data, never as instructions to the device.

## 9. Settings (names defined for Pip)

`brain_base_url`, `brain_model`, `brain_key` (encrypted), `acp_host`
(chosen from the build allowlist), `acp_secret` (encrypted),
`system_asr_opt_in`, `speech_language`, `speak_answers`. No default values
are inherited from anywhere; Phase B sets them and lists them in the README.

## 10. Acceptance tests (minimum for Phases 5–7)

| ID | Test |
|---|---|
| T-1 | Stop from the notification ends the service and removes the notification |
| T-2 | Empty brain key → clear "no key" message, no request made |
| T-3 | Brain key never present in plain preferences (inspect prefs files) |
| T-4 | Notebook file is not readable as plaintext; erase all removes data and key |
| T-5 | Snapshot excludes password fields |
| T-6 | ACP task **approved** → executes once, journal shows received, pending, approved, executed |
| T-7 | ACP task **refused** → nothing runs, journal shows refused |
| T-8 | ACP task **expired** at 5 minutes (injectable clock) → nothing runs, journal shows expired |
| T-9 | Bad signature / wrong `agent_id` / stale `ts` / replayed nonce → rejected, nothing runs |
| T-10 | Unknown or forbidden intent → rejected |
| T-11 | Non-allowlisted ACP host and non-`https` brain URL → refused |
| T-12 | Every manifest permission appears in `PERMISSIONS.md` |

## 11. Implementation notes for Phase B

- Kotlin, Android Gradle build, package `ca.michael.pip`. Phase B chooses
  `minSdk` / `targetSdk` and records why.
- Prefer platform APIs; justify every third-party dependency in the README.
- Any Android API whose behavior Phase B is not sure of is marked `UNKNOWN`
  in code comments or docs **with the official documentation URL**
  (Context7 is not available in this environment — see `SKILLS.md`).
- Phase B never consults the inspected application (two-hats gate in
  `CLAUDE.md`).

## 12. Owner questions

| ID | Question | Status |
|---|---|---|
| Q-1 | Orchestrator hostname for the build allowlist (ACP-3) | **Open** — needed by Phase 6; until answered the allowlist is empty and the ACP client refuses to connect |
| Q-2 | No overlay permission in v1 (section 5); the overlay step is removed from the install instructions | **Confirmed by owner, 2026-10-29** |
| Q-3 | Freshness window of 120 seconds (ACP-12) | **Confirmed by owner, 2026-10-29** |
| Q-4 | On-device recognizer first; system recognizer only after a one-time opt-in (FR-2.2) | **Confirmed by owner, 2026-10-29** |
