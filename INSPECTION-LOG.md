# Inspection Log

Traceability record for the clean-room boundary of Pip.

Every source consulted during the inspection phase (Phase A) is recorded here:
what was read, which lines, when, and why. Only behavior and concepts are
recorded — never code, UI strings, assets, or secret values.

## Rules for this log

- No decompiled or copied source code is ever pasted here.
- No secret value (key, token, password) is ever written here; a finding is
  recorded by location and type only.
- Settings are named by name only; their default values are not recorded.
- The inspection specimen APK and the reference repository live outside this
  repository and are never committed.
- Once `SPEC.md` is committed, Phase A is closed. Any later consultation of
  the original requires explicit owner approval and is logged in the
  "Post-closure consultations" section below.

## Inspection inputs

| Input | Location (outside this repo) | Identity | Mode |
|---|---|---|---|
| Reference repository | `/home/user/kuafuai/andee` (shallow clone of `https://github.com/kuafuai/andee`) | commit `df667e69a342d462ff0bb4593a73d84a9509d059` (committed 2026-10-29T18:09:19+08:00), CHANGELOG top release 0.8.0 | read-only |
| Specimen APK | `/root/.claude/uploads/628439d9-0d46-5db5-b901-e166ce62af1e/3fde90fe-Andee.apk` | 23,683,444 bytes, SHA-256 `2a1df7e52965931284c40fd9951a2888dc2a0b097045caee9772299c9f5d043c`, versionName 0.7.1, versionCode 17 | static inspection only — never installed, executed, redistributed, or committed |

## Tools actually used

REA (MCP `rea-agents` / skill `reverse-engineer-anything`) is **absent** from
this environment (see `SKILLS.md`). Explicit fallback, all read-only:

| Tool | Version | Use |
|---|---|---|
| apktool | 2.10.0 | `d -s` — decode manifest and resources, sources left as dex |
| jadx | 1.5.1 | `--no-res` — decompile dex for spot checks (finished with 28 errors) |
| keytool | JDK 21 | read the APK signing certificate |
| unzip / grep / python3 `-I` | system | list entries, pattern scans, shape checks that never print values |

Output directories (scratchpad, outside this repo, never committed):
`/tmp/claude-0/-home-user-pip/628439d9-0d46-5db5-b901-e166ce62af1e/scratchpad/insp/{apktool-out,jadx-out}`.

Path prefixes used below: `REF:` = reference repository root,
`REF-APP:` = `REF:app/src/main/java/net/kuafuai/andee/`,
`REF-CORE:` = `REF:core/src/main/kotlin/net/kuafuai/andee/`,
`APK-MAN:` = decoded `AndroidManifest.xml`,
`APK-SRC:` = jadx output `sources/net/kuafuai/andee/`.

## Consultation entries

| # | Date (UTC) | Phase | Source | Lines / scope | Purpose |
|---|---|---|---|---|---|
| 1 | 2026-10-29 | 0 | `REF:LICENSE` | grep over whole file (325 lines) | locate additional conditions |
| 2 | 2026-10-29 | 0 | `REF:LICENSE` | 1–120 | read Part A — A.1(a) L39–49, A.1(b) L51–56, A.1(c) L58–62, A.1(d) L64–81, A.3 L105–111 |
| 3 | 2026-10-29 | 1 | specimen APK | file metadata only | confirm receipt and identity |
| 4 | 2026-10-29 | 2 | specimen APK | signing block (`keytool -printcert`), entry list | signature and assets check |
| 5 | 2026-10-29 | 2 | `APK-MAN` | whole file (106 lines) | permissions, components, exported flags, app flags |
| 6 | 2026-10-29 | 2 | APK `res/xml/network_security_config.xml`, `res/xml/accessibility_service_config.xml`, `apktool.yml` | whole files | network policy, accessibility capabilities, SDK levels |
| 7 | 2026-10-29 | 2 | APK `assets/` | shape only (length, character class, documented prefix match) — value never printed | APK alert |
| 8 | 2026-10-29 | 2 | `REF:settings.gradle`, `REF:README.md` L127–177, L346–368, L574–604 | sections | modules, stated scope, architecture, permission policy |
| 9 | 2026-10-29 | 2 | `REF:docs/permissions.md` | L18–58, L104–110 | permission ↔ feature map, data flows |
| 10 | 2026-10-29 | 2 | `REF-APP:net/BodyWsServer.kt` | 30–175 | local WebSocket server: envelopes, bind, access checks |
| 11 | 2026-10-29 | 2 | `REF-APP:BodyCoreService.kt` | 1891–1964, 2550–2575, 3275–3300, 3451 | notification, WS server start, hub client, port constant |
| 12 | 2026-10-29 | 2 | `REF-APP:net/CommandDispatcher.kt` | 290–360, 2182 | dispatcher entry, guards |
| 13 | 2026-10-29 | 2 | `REF-APP:config/VoiceConfig.kt` | 40–56, 225–260, 622–650, 982–1014 | key storage, settings names |
| 14 | 2026-10-29 | 2 | `REF-CORE:peer/PeerLink.kt` | 100–135 + keyword scan | peer link trust model |
| 15 | 2026-10-29 | 2 | `REF-CORE:brain/LocalBrain.kt` | keyword scan, 963–972 | what reaches the model |
| 16 | 2026-10-29 | 2 | `REF` + `APK-SRC` | keyword scans (port, bind, AES/GCM, Keystore, prefs, URLs, credential patterns) | cross-check source vs APK |
| 17 | 2026-10-29 | 2 | file lists of `app/`, `core/`, `desktop/` modules | names only | block architecture |

## Phase 2 findings

Each finding is stated in my own words. "Verified in APK" means confirmed
directly in the decoded or decompiled specimen, not only in the repository
source (the APK is 0.7.1; the repository HEAD is 0.8.0).

### F1 — APK alert (security)

| Item | Finding | Proof |
|---|---|---|
| Signing | **Confirmed debug-signed.** Certificate subject and issuer are the generic Android debug identity, serial 1, valid 2025-04-28 → 2055-04-21, **SHA1withRSA** (keytool flags it as weak), 2048-bit RSA. Cert SHA-256 fingerprint `37:F1:0E:8D:83:9B:17:F8:AA:7B:CF:78:8E:59:7B:14:71:EF:CB:77:9F:90:6F:94:B5:57:9E:08:8D:B0:C5:93` | `keytool -printcert -jarfile` output |
| Debuggable | `android:debuggable="true"`, `BUILD_TYPE = "debug"` | `APK-MAN:34`, `APK-SRC:BuildConfig.java:6` |
| **Plaintext credential in assets** | **Confirmed.** `assets/andee_key.txt` holds a **36-byte non-empty token** whose prefix matches the per-user service token format the source documents. In the repository the same file is 1 byte (empty line), so the token was injected at packaging time. The app reads it as the built-in key for its default LLM/ASR/TTS gateway provider. **Value never printed or recorded.** | APK entry list (36 bytes); `REF:app/src/main/assets/andee_key.txt` (1 byte); `REF-APP:config/VoiceConfig.kt:228-246` |
| Other credential patterns | One `sk-`-shaped match in `classes19.dex` is a **false positive**: it sits immediately after an R8 synthetic-lambda marker, i.e. a generated method name, not a key. No match for Google API key, GitHub token, AWS key, or PEM private key patterns. `CODEFLYING_KEY` in BuildConfig is empty. | pattern scan counts; context check printing only the preceding marker; `APK-SRC:BuildConfig.java:9` |

**Recommendation:** treat the asset token as compromised — revoke / rotate it
with the issuing provider. The APK must not be installed, redistributed, or
committed. It stays outside this repository.

### F2 — Visible functions

| Function | What it does (concept) | Proof |
|---|---|---|
| Resident service | One foreground service is the whole "body"; no main UI. Declared types: microphone, media projection, special use. Ongoing notification with actions to open the app, fix accessibility, or request screen capture. **No Stop action** was seen in the notification builder. | `APK-MAN:42-44`; `REF-APP:BodyCoreService.kt:1891-1964` (`setOngoing` at 1918) |
| Voice in | Streaming cloud speech recognition (two providers selectable) plus an offline wake word matched on-device against user-enrolled templates (MFCC + DTW). | `REF-CORE:asr/HuoshanAsr.kt`, `REF-CORE:asr/GoogleAsr.kt`, `REF-APP:wake/WakeWord.kt`, `REF-CORE:wake/Mfcc.kt`, `REF-CORE:wake/Dtw.kt`; `REF:docs/permissions.md:41` |
| Voice out | Cloud TTS (two providers) plus Android system TTS. | `REF-CORE:tts/*`, `REF-APP:tts/SystemTts.kt:46` |
| Screen reading **and acting** | An `AccessibilityService` reads the UI tree and also taps, types, swipes and screenshots. | `APK-MAN:36-41`; APK `res/xml/accessibility_service_config.xml:2`; `REF-APP:ScreenBodyService.kt:38`; `REF:docs/permissions.md:40` |
| Local memory | Private notebook and a credentials vault, each an AES/GCM blob keyed in Android Keystore; plus chat history, people, plans, wiki. | `REF-APP:config/Notebook.kt:31,337-361`, `REF-APP:config/Vault.kt:33,242-271` |
| Brain | An agent loop on the device calls an LLM provider in a non-streaming loop with a step cap; each tool call goes to one central dispatcher. Alternative "hub" mode hands turns to a remote orchestrator. | `REF:README.md:352-363` |
| Channels | (a) outbound WebSocket client to a user-set hub URL; (b) inbound local WebSocket server on port 9008; (c) LAN peer link to other paired devices with mDNS discovery; (d) "call extension" role on the 9008 socket; (e) notification listener relay; (f) USB robot-dog link; (g) version check to a maintainer server. | `REF-APP:BodyCoreService.kt:3277-3292`, `REF-APP:net/BodyWsServer.kt:30-48`, `REF-CORE:peer/PeerLink.kt:117-129`, `REF-APP:device/NotificationRelayService.kt:53`, `REF-APP:dog/DogLink.kt:62`, `REF:docs/permissions.md:109` |
| Other surfaces | Floating ball avatar overlay, meeting recorder, camera "look" and barcode scan (ML Kit), scenes (long-running routines), quiet-hour review, nudges, market tools. | `REF-APP:ui/`, `REF-APP:meeting/MeetingRecorder.kt:64`, `APK-MAN:63-64`, APK `assets/mlkit_barcode_models/` |

### F3 — Block architecture (concepts)

- **Three modules:** a shared plain-JVM core (agent loop, prompts, tool
  schemas, peer protocol, speech engines), an Android body, a macOS body.
  Each body implements one host interface; the core never sees a platform
  type. (`REF:settings.gradle`, `REF:README.md:348-350`)
- **Turn sources:** wake word, voice or typing, a notification, a timer, a
  WebSocket request. (`REF:README.md:352-355`)
- **Flow:** turn → agent loop → model → tool calls → single dispatcher →
  accessibility / device controllers → results back to the model → output
  via speech, text, full-screen page, artifacts, long text, or motion.
- **Protocols:** JSON envelopes over WebSocket (request / response / event /
  register) for the 9008 socket and the hub; an encrypted, key-pinned
  session protocol for the peer link; HTTPS to model and speech providers.
- **What reaches the model:** in local-brain mode the notebook *index* is
  inlined into the user message sent to the LLM provider
  (`REF-CORE:brain/LocalBrain.kt:963-972`); the notebook *tools* are marked
  local-only and dropped from the hub register
  (`REF-CORE:net/ToolSchemas.kt:17,53`).

### F4 — Permissions and the function that justifies each

From `APK-MAN:2-33`; justification from `REF:docs/permissions.md:37-58`.

| Permission | Justifying function |
|---|---|
| `SYSTEM_ALERT_WINDOW` | the visible avatar overlay — the only all-or-nothing grant |
| `INTERNET`, `ACCESS_NETWORK_STATE` | model, speech, hub, version check |
| `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE` | speech recognition, wake word, meeting recorder |
| `QUERY_ALL_PACKAGES` | list and launch installed apps |
| `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE` | LAN peer discovery |
| `PACKAGE_USAGE_STATS` | not in the permissions doc table — **UNKNOWN** justification |
| `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | "where am I" |
| `ACTIVITY_RECOGNITION`, `HIGH_SAMPLING_RATE_SENSORS` | step count; sensors entry **UNKNOWN** justification |
| `READ_CONTACTS`, `CALL_PHONE`, `READ_CALL_LOG` | find contact, dial, call history |
| `READ_SMS`, `SEND_SMS` | read verification codes, send SMS |
| `READ_CALENDAR`, `WRITE_CALENDAR` | read and add events |
| `WRITE_SECURE_SETTINGS` | fallback input-method switch; signature/privileged level, only grantable via adb |
| `CAMERA` | camera look, barcode scan |
| `RECEIVE_BOOT_COMPLETED` | rebuild alarms after reboot |
| `POST_NOTIFICATIONS` | reminders and the persistent notification |
| `FOREGROUND_SERVICE`, `_SPECIAL_USE`, `_MEDIA_PROJECTION` | the resident service and screen capture |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | timely reminders with screen off |
| app-defined signature permission | AndroidX dynamic-receiver protection (library-generated) |

Stated policy: permissions are checked per call and refusal degrades only
the matching feature (`REF:README.md:582-588`,
`REF-APP:device/DeviceCommsController.kt:215`). Runtime behavior **UNKNOWN**
(nothing was executed).

### F5 — Security mechanisms observed

Owner's prior audit claims, each re-checked:

| Claim | Status | Proof |
|---|---|---|
| WebSocket server on 9008 bound to all interfaces, no visible authentication | **Confirmed, with nuance.** Default bind host is the wildcard address and the start site does not override it; the port is 9008. Every new connection is accepted as a driver with no token or handshake check. Only two debug methods are restricted to loopback; all other dispatcher commands (screen reading and acting included) are reachable by any LAN peer. A lock-screen guard refuses screen commands while the device is locked. The "passive" flag on this path changes UI behavior only, not access. | `REF-APP:net/BodyWsServer.kt:52-57,85,98-102,143-175`; `REF-APP:BodyCoreService.kt:2553-2572,3451`; `REF-APP:net/CommandDispatcher.kt:298-305,351`; **verified in APK**: `APK-SRC:net/BodyWsServer.java:55`, `APK-SRC:BodyCoreService.java:144,2964` |
| Cleartext traffic allowed to a hard-coded IP | **Confirmed.** The network security config permits cleartext to `124.71.176.202` (with subdomains). The specimen (debug variant) also permits cleartext to `127.0.0.1` and `localhost`. | APK `res/xml/network_security_config.xml:3-9`; `REF:app/src/main/res/xml/network_security_config.xml:13-15`; `REF:app/src/debug/res/xml/network_security_config.xml:22` |
| Keys stored in clear in preferences | **Confirmed.** Provider API keys (names: `api_key`, `codeflying_key`, `google_api_key`, `llm_api_key`) are written as plain strings into an ordinary private SharedPreferences file. | `REF-APP:config/VoiceConfig.kt:62,629-646,982-1014`; **verified in APK**: `APK-SRC:config/VoiceConfig.java:75-76,142` |
| AES/GCM storage backed by Keystore alongside | **Confirmed** for the notebook, the credentials vault, and the peer identity store. | `REF-APP:config/Notebook.kt:337-361`, `REF-APP:config/Vault.kt:242-271`, `REF-APP:device/PeerKeystore.kt:16-43`; **verified in APK**: `APK-SRC:` `config/Notebook.java`, `config/Vault.java`, `device/PeerKeystore.java`, `peer/PeerCrypto.java` contain AES/GCM |

Other mechanisms, good:

- `android:allowBackup="false"` (`APK-MAN:34`).
- Peer link: binds all interfaces **but** trusts nothing until a handshake
  verifies a signature from a pinned key; pairing only while a one-time code
  window is open; session payloads encrypted (`REF-CORE:peer/PeerLink.kt:100-145`).
- Turn-origin guards: a silent review may only touch notebook tools; a turn
  started by outside text (a notification) is refused vault access
  (`REF-CORE:net/TurnGuards.kt:49`, `REF-APP:net/CommandDispatcher.kt:298-325`).
- Credentials are typed by the device on request; the model never receives
  the secret itself (`REF-CORE:net/ToolSchemas.kt:676,686`).
- Lock-screen guard on screen and launch actions (`REF-APP:net/CommandDispatcher.kt:351,2182`).

Other mechanisms, weak:

- Debug build: debuggable, SHA1withRSA debug key, an **exported** debug
  activity (`APK-MAN:35`).
- Broad accessibility capabilities: gestures, screenshots, interactive
  windows, view IDs, touch-exploration requests (APK `accessibility_service_config.xml:2`).
- High-risk permission set (SMS, call log, contacts, installed apps, secure
  settings) — the source itself acknowledges it matches banking-trojan
  detection profiles (`REF:README.md:576-580`).
- Hub URL scheme is user-set; whether a `ws://` hub is blocked by the network
  policy is **UNKNOWN** (the WebSocket library uses its own sockets; not
  verified at runtime).

### F6 — Settings (names only, no defaults recorded)

From the allowed-settings list (`REF-APP:config/VoiceConfig.kt:982-1014`):
`mute`, `theme`, `uid`, `api_key`, `codeflying_key`, `asr_endpoint`,
`asr_resource_id`, `tts_endpoint`, `tts_resource_id`, `tts_sample_rate`,
`voice_provider`, `speak_provider`, `google_api_key`, `google_base_url`,
`google_project`, `google_location`, `google_asr_model`, `google_tts_model`,
`google_language`, `hub_url`, `device_name`, `brain`, `show_hub`,
`llm_api_key`, `llm_base_url`, `llm_model`, `llm_thinking`,
`llm_reasoning_effort`, `grounding`, `notify`, `nudge`, `drive`,
`timeline_events`, `locator_model`, `lang`, `ball`, `sweep`,
`sweep_quiet_minutes`, `sweep_daily_cap`, `max_steps`.

Build-time configuration names: `CODEFLYING_ENABLED`, `CODEFLYING_KEY`,
`CODEFLYING_DOMAIN`, `CODEFLYING_SSL_ENABLED`, `DEFAULT_LANG`,
`DEFAULT_THINKING` (`REF:app/build.gradle:150-155`).

### UNKNOWN — not established, with reason

| # | Item | Reason |
|---|---|---|
| U1 | Whether the asset token is still valid, and which account it belongs to | Testing it would mean using a secret; forbidden |
| U2 | Who built the specimen APK, and from which exact commit | Only zip timestamps (2026-10-29 00:43) and versionName 0.7.1 are known; repo clone is HEAD 0.8.0 |
| U3 | Full diff between the specimen (0.7.1) and the cloned source (0.8.0) | Only the points marked "verified in APK" were cross-checked |
| U4 | Real LAN reachability of port 9008 on a device | Nothing executed; depends on device and network |
| U5 | Whether a `ws://` hub URL is blocked by the network security policy | Raw-socket library behavior not verified at runtime |
| U6 | Justification for `PACKAGE_USAGE_STATS` and `HIGH_SAMPLING_RATE_SENSORS` | Absent from the permissions doc table; code not traced |
| U7 | Whether chat history is stored encrypted | Not inspected |
| U8 | What the 28 jadx decompile errors hide | Not investigated; spot checks did not need those classes |
| U9 | Runtime permission behavior (per-call checks, degradation) | Read from docs and one call site; not executed |
| U10 | The "never enters a payment password" boundary | Stated in `REF:README.md:136-139`; enforcing code not located |
| U11 | The macOS body (`desktop/`) | Out of Pip's Android scope; file names listed only |

## Phase 3 — SPEC.md and closure of Phase A

| # | Date (UTC) | Phase | Source | Lines / scope | Purpose |
|---|---|---|---|---|---|
| 18 | 2026-10-29 | 3 | this log only (findings F1–F6, UNKNOWN list) | — | write `SPEC.md`; the reference repository and the APK were **not** reopened |

Owner decision recorded for the spec: the notebook never leaves the device by
default; any excerpt sent to the brain needs the owner's explicit approval
per turn (`SPEC.md` FR-5.4, FR-5.5).

Clean-up at closure: the decoded and decompiled APK outputs
(`scratchpad/insp/apktool-out`, `scratchpad/insp/jadx-out`, which also held
a copy of the asset token) were deleted. The specimen APK and the reference
clone remain outside this repository for post-closure consultations only.

**Phase A is closed with the commit that adds `SPEC.md`.** From that commit
on, implementation uses `SPEC.md` only. Phase B runs in a fresh session that
does not receive the reference repository, the specimen APK, or any
decompiled output.

## Post-closure consultations

None.
