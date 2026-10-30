# Push to talk — short design

Status: **draft, waiting for the owner's approval** (2026-10-30).
Size: small change.

## Intent

The owner wants to talk to Pip with one quick gesture from anywhere, without
opening the app first. Their words: « appuyer sur P pour parler, ça me va ».
They do **not** want an always-listening wake word.

## Goals

- **Push to talk via the system assistant gesture.** Pip can be chosen as
  Android's default digital assistant. The phone's assistant gesture (long
  press on power or home, depending on the phone) then opens Pip already
  listening.
- **« Parler » action on the S1 notification**, as a fallback that works even
  when Pip is not the default assistant.

## Non-goals

- A wake word, or any listening the owner did not start.
- A Quick Settings tile (it would give nothing more than the notification
  action).
- Answering over the lock screen.

## Anti-goals (a failure even if it works)

- The mic opens without a gesture from the owner.
- Pip becomes the assistant without the owner choosing it in Android
  settings.
- A new hidden or background listening path.

## Constraints

- **CLAUDE.md:** visible always (S1 shows Pip's state), no invisibility, the
  mic only on the owner's action (FR-2.1).
- **Android:** to qualify for the assistant role, an app either implements
  `VoiceInteractionService` **or** handles `android.intent.action.ASSIST`.
  Source: AndroidX `RoleManagerCompat.ROLE_ASSISTANT` documentation,
  <https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/core/core-role/src/main/java/androidx/core/role/RoleManagerCompat.java>.
- **Phone settings:** which button triggers the assistant depends on the
  phone. Samsung, for example, ships the side key set to its own assistant.
  The owner may have to change it once. **UNKNOWN** for the owner's phone.

## Approaches (picked: 1 + 2)

| # | Approach | Trade-off | Decision |
|---|---|---|---|
| 1 | Default assistant through an `ASSIST` intent filter on `MainActivity` | One gesture from anywhere; no new service, no new permission, no new exported component (the launcher activity is already exported) | **Picked** |
| 1b | Default assistant through `VoiceInteractionService` | Heavier (three components, system binding); nothing more for the owner | Dropped |
| 2 | « Parler » button on the S1 notification | Two gestures, trivial to build, works without the assistant role | **Picked** |
| 3 | Quick Settings tile | Same gesture count as 2 | Dropped |

## How (decided)

- `MainActivity` gets an intent filter `android.intent.action.ASSIST` +
  `android.intent.category.DEFAULT`. An `ASSIST` intent opens Pip listening,
  through the same path as the bubble (`EXTRA_TALK`).
- S1 gets a third action, « Parler » / "Talk", opening `MainActivity` with
  `EXTRA_TALK`.
- Settings (S4) gets a « Faire de Pip l'assistant » button that opens the
  system screen where the owner chooses the default assistant
  (`Settings.ACTION_VOICE_INPUT_SETTINGS`, falling back to
  `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS`).

## My calls (please check)

1. **If Pip is off when the gesture comes, Pip turns itself on and listens.**
   The gesture is an explicit owner action, so this matches FR-1.1, and the
   S1 notification appears as usual. The other option: open Pip and ask the
   owner to tap « Allumer Pip » first.
2. **Pip does not ask Android directly to become the assistant** (no
   `RoleManager` request dialog). The owner picks it in Android's own screen,
   so the choice is always visibly theirs.

## Left to the builder

- Wording of the two new strings, in Pip's voice (BRAND.md).
- Tests: the core holds no logic for this, so the proof is the CI build, the
  merged-manifest check, and the owner's device checks:
  - assistant gesture opens Pip listening;
  - « Parler » on S1 opens Pip listening;
  - with Pip off, the gesture turns Pip on, and the notification appears.
