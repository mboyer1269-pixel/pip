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
- [ ] Collect the findings.
- [ ] Verify each finding against the code.
- [ ] Fix the confirmed ones (Task 5).

## Task 2 — Push to talk (approved design)

- [ ] `MainActivity` handles `android.intent.action.ASSIST`.
  - Pip turns on if it is off (my call 1), then listens.
- [ ] S1 gets a « Parler » action opening `MainActivity` with `EXTRA_TALK`.
- [ ] S4 gets a « Faire de Pip l'assistant » button. It opens
  `Settings.ACTION_VOICE_INPUT_SETTINGS`, with fallback
  `ACTION_MANAGE_DEFAULT_APPS_SETTINGS`.
- [ ] Update `SPEC.md` (new §16), strings (FR + EN) and `BRAND.md` lines.

## Task 3 — « Regarde l'écran » from the bubble (closes Q-5)

The problem: when Pip fills the screen, there is no other app to read
(README "Open points"). The bubble sits over the other app.
- [ ] A **long press** on the bubble is an explicit owner request (FR-3.1).
  - It captures the current app through the accessibility service.
  - Pip's own windows stay excluded: the bubble is an overlay, not an
    application window.
  - Pip then opens listening, with that snapshot attached to the next turn
    and shown under « Voir ce que j'ai lu ».
- [ ] If accessibility is off, it opens Pip with the existing hint, and
  nothing is read.
- [ ] Update `SPEC.md` (FR-3.1 and §15 FR-B.4), then mark Q-5 resolved by
  this gesture.

## Task 4 — Fun pack (Pip's personality, no new permission)

- [ ] **Time-of-day greeting** on S2, in Pip's voice: morning, afternoon,
  evening, night.
- [ ] **Tap Pip's face:** a wink, plus a short random line from a small
  original set.
- [ ] **Suggestion chips** on S2 that fill and send a turn:
  - « Qu'est-ce que j'ai demain ? »
  - « Résume mes notifications »
  - « Regarde mon écran »
  - « Ajoute une note »

  Each chip only starts a normal turn. Actions still go through S10.
- [ ] **Success wink:** the face shows SUCCESS for 2 s after an approved
  action ran.
- [ ] Strings FR + EN, and a `BRAND.md` section for these lines.

## Task 5 — Fix confirmed review findings

- [ ] One commit per finding or small group, each with its reason.

## Task 6 — Ship

- [ ] Gates: `clean-room-gate.sh`, `check-permissions.sh`, `:core:test`.
- [ ] Commit and push, open a draft PR, watch CI until green, merge.
- [ ] Give the owner the APK link and sha256, and a short device test list.

## Out of scope (stated, not built)

- Wake word: the owner chose push-to-talk.
- Blocks D, E and F:
  - D, actions inside other apps, has a high misuse risk; it needs its own
    brainstorm;
  - E is the camera;
  - F is multi-device.
- ACP live use waits for Q-1 (the orchestrator domain).
