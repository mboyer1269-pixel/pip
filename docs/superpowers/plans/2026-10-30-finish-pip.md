# Finish Pip — Implementation Plan

> **For agentic workers:** use superpowers:executing-plans to run this plan
> task by task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Finish Pip as a solid, autonomous and fun pocket companion on the
owner's phone, within `CLAUDE.md` and `SPEC.md`.

**Architecture:** Kotlin, two modules.
- `:core` is pure JVM logic, unit-tested: protocol, actions, text.
- `:app` holds the platform glue: service, voice, accessibility, overlay,
  UI.
- CI on GitHub Actions builds the debug APK; there is no local Android SDK.

**Tech stack:** Kotlin 2.0.21, AGP 8.7.3, platform Views, OkHttp
(ACP only), kotlinx-serialization-json.

**Spec:** `SPEC.md` (sections 1–15), `BRAND.md`,
`docs/superpowers/specs/2026-10-30-push-to-talk-design.md`.

**Owner authorization:** the `/goal` of 2026-10-30 asks to plan and then
complete the plan autonomously. It covers:
- the push-to-talk design, with its two "my calls";
- the tasks below.

Tasks that change scope update `SPEC.md` first, as before.

## Global constraints

- Clean-room: never open `INSPECTION-LOG.md`, the reference app, or any
  decompiled output. Agents are told so.
- Visible always: no listening, reading or sending without an owner gesture
  and S1 showing.
- Every action that reads or sends personal data goes through the S10
  approval card. No "always allow".
- Every new permission has a row in `PERMISSIONS.md`
  (`scripts/check-permissions.sh` must exit 0).
- `scripts/clean-room-gate.sh` exits 0 before every commit.
- User-visible text: French first, then English, in Pip's voice (BRAND.md).
- Proof is the CI run: green build, the APK sha256, the gitleaks scan. No
  "done" without that output.

## Review focus

Cases the tests do not exercise, to check on the device:

1. Bubble gestures while another app is in the foreground (tap vs drag vs
   long press).
2. Assistant gesture when Pip is off, or when notifications are refused.
3. A new turn started while an S10 card or a permission prompt is open.
4. Process death: the service is restarted by the system (START_NOT_STICKY
   means it must not be).
5. Locale switch FR ↔ EN for every new string.

## Task 0 — Merge the bubble (done)

- [x] PR #5 green on `9713e96` (runs 31/32), merged as `0f9b14b`.

## Task 1 — Independent code review of blocks A, C and the bubble

Two Sonnet agents, read-only, in parallel. One reviews actions and the home
flow; the other reviews the service, bubble, listener and manifest.
Result: 13 findings; 12 confirmed and fixed in `0f47543` and `c429835`. One
was dropped: the notebook is cached after its first read, so the 30 s
reminder check does no repeated disk work.
- [x] Collect the findings.
- [x] Verify each finding against the code.
- [x] Fix the confirmed ones (Task 5).

## Task 2 — Push to talk (approved design)

- [x] `MainActivity` handles `android.intent.action.ASSIST`.
  - Pip turns on if it is off (my call 1), then listens.
- [x] S1 gets a « Parler » action opening `MainActivity` with `EXTRA_TALK`.
- [x] S4 gets a « Faire de Pip l'assistant » button. It opens
  `Settings.ACTION_VOICE_INPUT_SETTINGS`, with fallback
  `ACTION_MANAGE_DEFAULT_APPS_SETTINGS`.
- [x] Update `SPEC.md` (new §16), strings (FR + EN) and `BRAND.md` lines.

## Task 3 — « Regarde l'écran » from the bubble (closes Q-5)

The problem: when Pip fills the screen, there is no other app to read
(README "Open points"). The bubble sits over the other app.
- [x] A **long press** on the bubble is an explicit owner request (FR-3.1).
  - It captures the current app through the accessibility service.
  - Pip's own windows stay excluded: the bubble is an overlay, not an
    application window.
  - Pip then opens listening, with that snapshot attached to the next turn
    and shown under « Voir ce que j'ai lu ».
- [x] If accessibility is off, it opens Pip with the existing hint, and
  nothing is read.
- [x] Update `SPEC.md` (FR-3.1 and §15 FR-B.4), then mark Q-5 resolved by
  this gesture.

## Task 4 — Fun pack (Pip's personality, no new permission)

- [x] **Time-of-day greeting** on S2, in Pip's voice: morning, afternoon,
  evening, night.
- [x] **Tap Pip's face:** a wink, plus a short random line from a small
  original set.
- [x] **Suggestion chips** on S2 that fill and send a turn:
  - « Qu'est-ce que j'ai demain ? »
  - « Résume mes notifications »
  - « Regarde mon écran »
  - « Ajoute une note »

  Each chip only starts a normal turn. Actions still go through S10.
- [x] **Success wink:** the face shows SUCCESS for 2 s after an approved
  action ran.
- [x] Strings FR + EN, and a `BRAND.md` section for these lines.

## Task 5 — Fix confirmed review findings

- [x] The fixes are grouped in `0f47543` (app) and `c429835` (core,
  test-first), with the reason for each in the commit message.

## Task 6 — Ship

- [x] Gates: `clean-room-gate.sh` exit 0, `check-permissions.sh` exit 0,
  `:core:test` 48/48 (local). In CI: all steps of runs 34 and 36 green, with
  gitleaks.
- [x] Committed (`c429835`, `0f47543`, `4f9d434`), PR #6 opened, CI green
  on the code head (run 34, `0f47543`) and on `4f9d434` (runs 36/37).
  Merge happens after CI is green on this commit.
- [x] APK (run 36, `4f9d434`): 4 685 247 bytes, sha256
  `f80f2769c3e26292666c364f9ca9223d790f793e3e855922507e3120cb47a272`,
  artifact `pip-debug-apk`
  <https://github.com/mboyer1269-pixel/pip/actions/runs/40984343136/artifacts/12946248137>.

## Task 7 — Device smoke test in CI (added after the goal check)

Nothing had run on a real Android system before this. Owner device tests stay
the reference, but CI should catch crashes first.
- [x] `scripts/smoke-test.sh` is written. It runs on an emulator and checks:
  - the app launches;
  - `ASSIST` turns Pip on (foreground `PipService` and S1);
  - the crash buffer stays empty.
- [x] Gates rerun on the merged `bb9e499`: clean-room exit 0, permissions
  exit 0, `:core:test` 48/48.
- [x] CI `smoke` job green on the PR head (run 40995340777, `cab472b`).
  - The first run failed on the test script, not on Pip: the short
    `dumpsys` output has no `isForeground` field, and the check ran before
    `startForeground`. Fixed in `cab472b`.
  - Log on the emulator (API 34): `ok installed`, `ok launched`,
    `ok ASSIST turned Pip on (foreground service running)`,
    `ok S1 notification posted`, `ok no crash`, `SMOKE PASS`.
  - Merge after CI is green on this commit.

## Task 8 — Owner decisions and more device checks in CI (after the goal check)

- [x] Q-6 resolved (re-post S1) and Q-1 deferred (no server yet), from the
  owner's answers; `SPEC.md` updated.
- [x] Smoke test extended: « Arrêter » on S1 stops Pip, the home then offers
  « Allumer Pip » (FR), and "Turn Pip on" shows in English.
- [x] Bug found by the emulator and fixed: with the app locale `[fr]`, the
  whole app showed English.
  - Android merges app and system locales (`[fr, en-US]`). With several
    locales, it matches only explicitly qualified resources, and French sat
    in unqualified `values/`.
  - The same bug hits a phone set to French then English.
  - Fix: French moves to `values-fr/`, and `values/` becomes the English
    fallback. `android:localeConfig` declares fr and en, so the owner can
    pick Pip's language in Android settings.
- [x] CI green on `22434d2` (runs 40999283018 / 40999278378): build, then
  smoke `ok Stop on S1 stopped Pip`, `ok home shows « Allumer Pip » (FR)`,
  `ok home shows "Turn Pip on" (EN)`, `SMOKE PASS`.
- [x] Stop tap made robust in `71c3b9e`: on `8035f4e` the push run passed
  but the pull_request run failed. The tap position had been read while the
  shade was still settling. The script now taps only after two identical
  reads and retries up to 3 times.
  - Runs 41000243820 / 41000238706, both green.
  - The home screen shows French end to end (« Il est tard… Pip chuchote :
    je suis là. », « Allumer Pip »).
  - Stop worked on attempt 1; `SMOKE PASS`.
- [x] Merge PR #8 after CI is green on its head.

Still owner-only, on the phone: drag and long press on the bubble, the
assistant gesture on the owner's phone model, the S10 approval card with a
live brain (approve / refuse / 1 s arming).

## Task 9 — Bubble on the emulator (after the goal check)

- [x] The smoke test checks the bubble (SPEC 15):
  - overlay permission pre-granted with `appops`;
  - Pip on, then « Afficher la bulle » ticked in Réglages;
  - from the launcher, Pip's `APPLICATION_OVERLAY` window is shown;
  - a tap on it makes `MainActivity` the resumed activity.
- [x] The first run showed the bubble, then the script exited silently.
  Cause: `dumpsys | grep -m1` inside a command substitution, under
  `set -e` and `pipefail`. Fixed in `11c53f8`.
- [x] CI green on `11c53f8` (runs 41002717640 / 41002714155):
  `ok bubble shown over another app (at 28 292)`,
  `ok tap on the bubble opened Pip`, `SMOKE PASS`.
- [ ] Merge PR #9 after CI is green on its head.

## Out of scope (stated, not built)

- Wake word: the owner chose push-to-talk.
- Blocks D, E and F:
  - D, actions inside other apps, has a high misuse risk; it needs its own
    brainstorm;
  - E is the camera;
  - F is multi-device.
- ACP live use waits for Q-1 (the orchestrator domain).
