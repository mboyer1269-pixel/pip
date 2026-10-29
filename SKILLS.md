# SKILLS.md — Tool inventory

Proven inventory of the skills, plugins, MCP servers and CLI tools available
to Claude Code sessions on this project. "Present" means usable in the
session at the time of the check; anything else is "Absent" with the reason.

- **Checked on:** 2026-10-29, cloud session for `mboyer1269-pixel/pip`.
- **Method:** session tool search, local skill/plugin directories
  (`~/.claude/skills`, `~/.claude/plugins`), local MCP config
  (`~/.claude.json`, repo `.mcp.json`), the claude.ai connector registry,
  the claude.ai plugin catalog, the enabled-skills list, and `command -v`.
- Re-check in every new session: availability is per session.

## Requested tools

| Tool | Status | Proof |
|---|---|---|
| **Context7** (docs lookup MCP) | **Absent** in session | Connector registry: `Context7`, `installState: connect_incomplete`, `connected: false`, `enabledInChat: false`. Plugin catalog: `context7` (Upstash), `enabled: false`. Not in the session tool list. 0 files matching `context7` under `~/.claude/skills` and `~/.claude/plugins`. |
| **21st** (21st.dev UI components MCP) | **Absent** in session | Plugin catalog: `21st` (21st.dev), `enabled: false`. Not in the session tool list. 0 files matching `21st` / `magic-mcp` locally. |
| **REA** (`morluto/rea`, MCP `rea-agents`, skill `reverse-engineer-anything`) | **Absent** | Not in the session tool list. Not in the connector registry, plugin catalog, or enabled skills. 0 files matching `rea-agents`, `morluto`, `reverse-engineer-anything` locally. A different catalog plugin named "Reverse Engineering" (author EncidXVII) exists, `enabled: false` — it is **not** REA and was not used. |
| **ECC** | **Present as a GitHub App, non-functional** | `ecc-tools[bot]` posted on PR #1 (commit `d929239`): audit blocked before scanning — private repository analysis requires a paid plan; check publication denied. No ECC skill, plugin, or MCP server in the session (0 files matching `everything-claude-code` / `ecc-tools` locally). |

## Consequence for Phase 2

REA is absent, so the inspection uses the explicit fallback: **jadx** and
**apktool**, read-only, plus standard CLI tools.

## CLI tools (inspection)

| Tool | Status | Proof |
|---|---|---|
| `jadx` | Present (installed this session) | `jadx-1.5.1.zip` from the official GitHub release, sha256 `12fd966431903b8e15c36e5007f19343475be7d8f2a55f082e7a929eeabc937e`, `--version` → `1.5.1`, at `/opt/re-tools/jadx` |
| `apktool` | Present (installed this session) | `apktool_2.10.0.jar` from the official GitHub release, sha256 `c0350abbab5314248dfe2ee0c907def4edd14f6faef1f5d372d3d4abd28f0431`, `--version` → `2.10.0`, at `/opt/re-tools/apktool.jar` |
| `java` | Present | OpenJDK 21.0.12.1 |
| `keytool` | Present | `/usr/bin/keytool` |
| `unzip`, `zipinfo`, `strings`, `python3` | Present | `/usr/bin/*` |
| `aapt`, `aapt2`, `apksigner`, `adb` | Absent | `command -v` returns nothing (no Android SDK) |
| `gitleaks` | Present (installed this session, Phase 3) | `gitleaks_8.21.2_linux_x64.tar.gz` from the official GitHub release, sha256 `5bc41815076e6ed6ef8fbecc9d9b75bcae31f39029ceb55da08086315316e3ba`, `version` → `8.21.2`, at `/usr/local/bin/gitleaks`; a fresh session must reinstall it |

## Project skills (vendored in `.claude/skills/`)

Five skills from Google's official Android skills repository
(`https://github.com/android/skills`, commit `6daa53ef27fa2aa87827d183a85c06cedceca833`,
Apache 2.0), copied unmodified after review: `android-permissions-security`,
`android-intent-security`, `testing-setup`, `anr-audit-and-fix`, `r8-analyzer`.
Details and rationale in `.claude/skills/README.md`. They load in any session
started on this repository.

The full Android Skills plugin (36 skills) was enabled briefly, then removed
at the owner's request (2026-10-29): no v1 feature needs the other 31 skills,
the Compose ones could pull Phase B out of scope, and an unpinned plugin
updates its instructions without review. Re-add individual skills one by one
when a v2 feature needs them.

## Other tools present that matter to this project

| Tool | Use |
|---|---|
| GitHub MCP (`mcp__github__*`) | Repo checks, PRs, CI status |
| Skills `code-review`, `security-review` | Review of Pip's own code (Phase B) |
| Firecrawl search | Public documentation lookup (Android APIs) |

## Not available in this environment

- **Android SDK / emulator / device** → no on-device test and no install from
  here; Phase 7 may need the GitHub Actions workflow route.
