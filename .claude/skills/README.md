# Project skills

Vendored from Google's official Android skills repository, Apache License 2.0
(a copy of the license sits in each skill folder).

- Source: https://github.com/android/skills
- Commit: `6daa53ef27fa2aa87827d183a85c06cedceca833` (2026-10-29)
- Copied unmodified. Reviewed before vendoring: no network or shell calls in the
  bundled scripts (`r8-analyzer/scripts/*.py` read local build outputs only).

| Skill | Why it is here |
|---|---|
| `android-permissions-security` | Audit the manifest, exported components and runtime permission flow against `SPEC.md` SEC-4 / SEC-6 |
| `android-intent-security` | Audit PendingIntents and incoming Intents (notification actions, activities) |
| `testing-setup` | Unit, instrumented and end-to-end test setup for Phase 7 |
| `anr-audit-and-fix` | Main-thread risks in the foreground service, voice and accessibility code |
| `r8-analyzer` | Keep rules for the minified release build (SEC-5) |

Not vendored: Compose, Wear, TV, XR, cars, camera, Play, identity and on-device
AI skills — no v1 feature uses them. Update by re-copying from a newer commit
and recording it above.
