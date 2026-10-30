# Camera "eye" (block E) — proposed design

Status: **PROPOSED, awaiting the owner's GO.** Nothing is built.
`SPEC.md` §2.2 and `CLAUDE.md` park this feature for v2. This note only
prepares the decision.

## Intent

The owner shows Pip something through the camera, then asks about it, for
example « C'est quoi ce voyant sur mon tableau de bord ? » or « Lis-moi
cette étiquette ». It should feel playful, like Pip opening one eye, never
like surveillance.

## Goals

- One owner gesture leads to **one photo**, which is shown back and then
  sent to the brain with the question. Nothing more.
- The flow reuses the existing turn: the photo replaces the screen snapshot
  for that turn, the same way the bubble long press does (FR-B.6).

## Non-goals

- Continuous video, live preview analysis, or a "watch" mode.
- Code or QR scanning: that is a separate later step.
- Storing photos. The picture lives in memory for one turn only.

## Anti-goals (a failure even if it works)

- The camera opens without an owner gesture on the device.
- A remote ACP task triggers a capture. This is already forbidden in §7.5,
  and it stays forbidden.
- A photo leaves the device without the owner seeing it first.

## Approaches

| # | Approach | Trade-off | Recommendation |
|---|---|---|---|
| 1 | System camera app through `MediaStore.ACTION_IMAGE_CAPTURE`, result returned to Pip | No `CAMERA` permission, because the system app takes the photo. The capture UI is Android's own. Lowest risk. | **Recommended** |
| 2 | In-app capture with Camera2 or CameraX | Custom "eye" UI (more fun), but it needs the `CAMERA` permission and a lot more code. | Later, if 1 feels too plain |
| 3 | Pick an existing photo from the gallery (photo picker) | No permission, also useful. It is not "looking", though. | Optional add-on to 1 |

Approach 1 constraint, **to verify when building**: an app that *declares*
`CAMERA` but does not hold it cannot use `ACTION_IMAGE_CAPTURE`. Pip must
therefore keep `CAMERA` out of its manifest. Source to cite at build time:
the Android `MediaStore.ACTION_IMAGE_CAPTURE` reference.

## Flow (approach 1)

1. A new chip on S2, « Regarde avec la caméra », or the owner says « regarde
   ça ».
2. The system camera opens. The owner takes the photo or cancels; a cancel
   leaves no trace.
3. Pip shows the photo, downscaled, with « Je l'envoie au cerveau ? » and
   two buttons: Envoyer and Annuler. This mirrors the S10 approval card,
   with Envoyer armed after 1 s.
4. On Envoyer, the image goes with the next turn as an image part of the
   chat message (OpenAI-compatible `image_url` with a base64 data URL), then
   the bytes are dropped.

## Unknowns (to settle before building, never guessed)

- **UNKNOWN:** does the owner's endpoint and model accept `image_url` parts
  through its OpenAI-compatible API? Check against the provider's current
  documentation, then with a live test from S4.
- **UNKNOWN:** the size limit for images on that endpoint. Pip downscales
  (for example to a 1024 px long edge, JPEG), and the final figure follows
  the documentation.
- **UNKNOWN:** whether the owner wants approach 3 (gallery) in the same
  block.

## Proof plan when built

- `:core` unit tests for building the image message part and for the
  "no image without confirmation" rule, test-first.
- `PERMISSIONS.md`: no new permission for approach 1, and
  `check-permissions.sh` proves it.
- Emulator smoke: the chip opens the system camera intent, and a cancel
  leaves no snapshot and no crash.
- Owner phone check: photo, then a question, then a sensible answer.
