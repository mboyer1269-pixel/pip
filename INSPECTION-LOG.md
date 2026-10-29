# Inspection Log

Traceability record for the clean-room boundary of Pip.

Every source consulted during the inspection phase (Phase A) is recorded here:
what was read, which lines, when, and why. Only behavior and concepts are
recorded — never code, UI strings, assets, or secret values.

## Rules for this log

- No decompiled or copied source code is ever pasted here.
- No secret value (key, token, password) is ever written here; a finding is
  recorded by location and type only.
- The inspection specimen APK and the reference repository live outside this
  repository and are never committed.
- Once `SPEC.md` is committed, Phase A is closed. Any later consultation of
  the original requires explicit owner approval and is logged in the
  "Post-closure consultations" section below.

## Inspection inputs

| Input | Location (outside this repo) | Identity | Mode |
|---|---|---|---|
| Reference repository | `/home/user/kuafuai/andee` (shallow clone of `https://github.com/kuafuai/andee`) | commit `df667e69a342d462ff0bb4593a73d84a9509d059` (committed 2026-10-29T18:09:19+08:00) | read-only |
| Specimen APK | `/root/.claude/uploads/628439d9-0d46-5db5-b901-e166ce62af1e/3fde90fe-Andee.apk` | 23,683,444 bytes, SHA-256 `2a1df7e52965931284c40fd9951a2888dc2a0b097045caee9772299c9f5d043c` | static inspection only — never installed, executed, redistributed, or committed |

## Consultation entries

| # | Date (UTC) | Phase | Source | Lines / scope | Purpose | Notes |
|---|---|---|---|---|---|---|
| 1 | 2026-10-29 | 0 | reference repo `LICENSE` | grep over whole file (325 lines) | locate the additional-conditions section | keyword search only |
| 2 | 2026-10-29 | 0 | reference repo `LICENSE` | lines 1–120 | read Part A (additional conditions) | findings summarized in conversation: A.1(a) L39–49, A.1(b) L51–56, A.1(c) L58–62, A.1(d) L64–81, A.3 L105–111 |
| 3 | 2026-10-29 | 1 | specimen APK | file metadata only (`ls`, `file`, `sha256sum`) | confirm receipt and identity | archive not opened, not unpacked, not executed |

## Post-closure consultations

None.
