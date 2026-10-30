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
offline wake word; any
local network server; screenshots or screen recording; changing system input settings; a remote "hub" mode
that hands the whole conversation to a server.

Moved into **Block A** (section 13, owner GO 2026-10-30): reading and
sending SMS; reading the call log and contacts; placing calls; calendar
access; location; step counting; launching installed apps.

Moved into **Block C** (section 14, owner GO 2026-10-30): reading other
apps' notifications, on request.

Moved into **Block B — bubble** (section 15, owner GO 2026-10-30): a
floating Pip drawn over other apps.

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
| S10 | **Action approval** (Block A) | One proposed device action in Pip's words: exactly what happens, to whom, what data is read and sent to the brain; a choice when a name matches several contacts | **Approve** (armed after 1 s) or **Refuse** — one action at a time, no "always" option |

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
- FR-4.7 The brain answers with text, or **proposes** device actions from
  the closed list of section 13. A proposal never runs by itself: each one
  goes through the owner's approval (FR-A.3).

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

Not requested: boot receiver; camera; `QUERY_ALL_PACKAGES`; usage-stats;
secure-settings. Block A adds its own permissions (section 13.4); the
Block B bubble adds the overlay permission (section 15).

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
`system_asr_opt_in`, `speech_language`, `speak_answers`, `actions_enabled`, `bubble_enabled`. No default values
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

## 13. Block A — Pip acts on the phone (owner GO 2026-10-30)

Pip can now act on the owner's phone, always with the owner's yes.

### 13.1 Principle

- FR-A.1 The brain may **propose** actions; only the closed list below
  exists, declared in code. Anything else is rejected and nothing runs.
- FR-A.2 Every action is shown on surface **S10 — Action approval** with
  Pip's own words saying exactly what will happen (recipient name and
  number, message text in full, event title and time, what data will be
  read and sent to the brain).
- FR-A.3 Nothing runs without **Approve** on S10. **Refuse** runs nothing
  and the brain is told "refused by the owner". No "always allow", no
  batch approval: one action, one decision. Approve is armed only after the
  card has been on screen for 1 second.
- FR-A.4 **Reads leave the device only after approval.** For a read action
  (SMS, call log, calendar, location, steps, contacts), S10 says that the
  result will be sent to the brain for this turn; refusal sends nothing.
- FR-A.5 Name resolution stays local. When a name matches several
  contacts, S10 lets the owner pick one; the candidate list is never sent
  to the brain.
- FR-A.6 At most 3 actions per turn and 2 follow-up rounds with the brain.
  A new turn cancels any action still waiting.
- FR-A.7 Each permission is asked at the moment an approved action first
  needs it. If refused, the action reports "permission refused" and
  nothing else happens. One exception: when the brain names a person,
  `READ_CONTACTS` is asked before S10 so the card can show the exact name
  and number; that lookup stays on the device (FR-A.5).
- FR-A.8 Remote ACP tasks still cannot send SMS or place calls (section
  7.5 is unchanged). Block A actions start only from the owner's own turn
  on the device.
- FR-A.9 The owner can turn actions off in Settings (`actions_enabled`);
  the brain then receives no action list.

### 13.2 Closed list of actions

| Action | Effect when approved | Arguments |
|---|---|---|
| `open_app` | Launch an installed app by its visible name | `app_name` |
| `call` | Place a call | `to` (name or number) |
| `send_sms` | Send an SMS | `to` (name or number), `text` (≤ 1000 chars) |
| `find_contact` | Read matching contacts (name, numbers) and send them to the brain | `name` |
| `read_sms` | Read recent received SMS and send them to the brain | `from` (optional), `limit` (1–10, default 5) |
| `read_call_log` | Read recent calls and send them to the brain | `limit` (1–10, default 5) |
| `read_calendar` | Read events in a date range and send them to the brain | `from_date` (YYYY-MM-DD, default today), `days` (1–14, default 1) |
| `add_calendar_event` | Add an event to the primary calendar | `title`, `start` (YYYY-MM-DDTHH:MM, local), `duration_minutes` (5–1440, default 60), `location` (optional) |
| `get_location` | Read the current position and send it to the brain | none |
| `get_steps` | Read the step counter (steps since the last reboot) and send it to the brain | none |

### 13.3 Brain protocol

The actions are offered to the brain as OpenAI-compatible `tools`
(function calling). Tool calls are untrusted proposals (SEC-9): arguments
are validated against the list above before S10 is shown. After the
owner's decisions, the tool results go back to the brain in the same turn
so it can answer in words. Conversation history keeps only the owner's
words and Pip's final answer, never tool results.

### 13.4 Permissions added by Block A

| Permission | Action |
|---|---|
| `CALL_PHONE` | `call` |
| `SEND_SMS` | `send_sms` |
| `READ_SMS` | `read_sms` |
| `READ_CALL_LOG` | `read_call_log` |
| `READ_CONTACTS` | `find_contact`; resolving a name for `call` / `send_sms` |
| `READ_CALENDAR`, `WRITE_CALENDAR` | `read_calendar`, `add_calendar_event` |
| `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | `get_location` (foreground only, never in the background) |
| `ACTIVITY_RECOGNITION` | `get_steps` |

Package visibility: a `<queries>` entry for launcher activities
(`MAIN`/`LAUNCHER`) so `open_app` can find apps. No `QUERY_ALL_PACKAGES`.

### 13.5 Acceptance tests

| ID | Test |
|---|---|
| T-A1 | A proposed action does nothing until Approve; Refuse runs nothing and the brain receives "refused" |
| T-A2 | An action name outside 13.2 is rejected; arguments out of range are rejected |
| T-A3 | A name matching several contacts shows a local choice; no candidate list reaches the brain |
| T-A4 | No more than 3 actions per turn are shown |
| T-A5 | With `actions_enabled` off, the request carries no tools |
| T-A6 | Every new permission appears in `PERMISSIONS.md` |

## 14. Block C — Pip reads notifications (owner GO 2026-10-30)

- FR-C.1 A notification listener service lets Pip read the notifications
  currently shown on the phone. The owner turns it on in the system
  "Notification access" screen; Pip opens that screen from S3 or when the
  action needs it.
- FR-C.2 The service reads **nothing on its own**: it does not react to
  notifications as they arrive. It reads only when an approved
  `read_notifications` action asks for it.
- FR-C.3 `read_notifications` is added to the closed list of section 13.2
  and follows every Block A rule (S10 card, one decision, reads sent to the
  brain only after approval).

| Action | Effect when approved | Arguments |
|---|---|---|
| `read_notifications` | Read the notifications currently shown (app, title, text, time) and send them to the brain | `app` (optional, app name), `limit` (1–20, default 10) |

- FR-C.4 Skipped: Pip's own notifications, ongoing notifications (media,
  navigation, downloads) and group summaries. Each text is cut at 200
  characters.
- FR-C.5 The service is protected by `BIND_NOTIFICATION_LISTENER_SERVICE`
  (held by the system only) and is the third exported component.

| ID | Test |
|---|---|
| T-C1 | `read_notifications` validates `limit` and `app` like other actions |
| T-C2 | With notification access off, the action says how to turn it on and sends nothing |

## 15. Block B — the bubble (owner GO 2026-10-30)

- FR-B.1 An optional floating bubble (Pip's pocket icon) drawn over other
  apps, so the owner can reach Pip without opening it. **Off by default**;
  the owner turns it on in Settings (`bubble_enabled`).
- FR-B.2 It needs the system "Display over other apps" permission
  (`SYSTEM_ALERT_WINDOW`), granted by the owner in system settings; Pip
  opens that screen when the owner turns the bubble on.
- FR-B.3 The bubble exists **only while Pip runs** (V1-1): it appears when
  the service starts with the setting on, and disappears on Stop. It never
  replaces the S1 notification, which stays as is.
- FR-B.4 Tap: Pip opens and starts listening (same as Talk on S2). Drag:
  moves the bubble; its place is remembered. The bubble reads nothing and
  sends nothing by itself.
- FR-B.5 The bubble window is not focusable and takes no input other than
  taps, long presses and drags on itself. Its place is clamped to the screen.
- FR-B.6 **Long press = « regarde l'écran ».** A long press on the bubble is
  the owner's explicit request (FR-3.1) to read the app underneath. The
  accessibility service captures it (Pip's own windows stay excluded,
  FR-3.4). Pip then opens listening, and the snapshot is attached to the
  next turn only and shown under « Voir ce que j'ai lu ». With accessibility
  off, Pip says how to turn it on and reads nothing. This answers the
  full-screen gap noted in Phase 5.

| ID | Test |
|---|---|
| T-B1 | Bubble off by default; turning it on without the permission opens the system screen |
| T-B2 | With Pip stopped there is no bubble; Stop removes it |
| T-B3 | Tapping the bubble opens Pip listening |

## 16. Push to talk (owner `/goal` 2026-10-30)

Design: `docs/superpowers/specs/2026-10-30-push-to-talk-design.md`.

- FR-P.1 `MainActivity` handles `android.intent.action.ASSIST`, so the owner
  can pick Pip as Android's digital assistant in system settings. Pip never
  requests the role itself. The assistant gesture opens Pip listening.
- FR-P.2 S1's second action is « Parler » / "Talk", which opens Pip
  listening. Tapping the notification itself still opens Pip.
- FR-P.3 If Pip is off when a push-to-talk gesture arrives, it turns on (the
  gesture is the owner's explicit action, FR-1.1) and then listens. If the
  notification permission is refused, the usual explanation is shown and
  nothing listens.
- FR-P.4 No wake word, no listening the owner did not start.

| ID | Test |
|---|---|
| T-P1 | With Pip chosen as assistant, the assistant gesture opens Pip listening |
| T-P2 | « Parler » on S1 opens Pip listening |
| T-P3 | With Pip off, the gesture turns Pip on (S1 appears) and listens |

## 12. Owner questions

| ID | Question | Status |
|---|---|---|
| Q-1 | Orchestrator hostname for the build allowlist (ACP-3) | **Deferred by the owner (2026-10-30):** no server yet. The allowlist stays empty and the ACP client refuses to connect; reopen when the owner gives a hostname |
| Q-2 | No overlay permission in v1 (section 5); the overlay step is removed from the install instructions | **Confirmed by owner, 2026-10-29**; superseded 2026-10-30 by the owner's GO for the bubble (section 15), which is off by default |
| Q-3 | Freshness window of 120 seconds (ACP-12) | **Confirmed by owner, 2026-10-29** |
| Q-4 | On-device recognizer first; system recognizer only after a one-time opt-in (FR-2.2) | **Confirmed by owner, 2026-10-29** |
| Q-5 | How to read the screen when Pip fills it | **Resolved 2026-10-30** by the bubble long press (FR-B.6), under the owner's `/goal` |
| Q-6 | Swiping S1 away: re-post or stop Pip | **Resolved by the owner (2026-10-30):** re-post S1 (FR-1.2). Only the Stop action turns Pip off |
