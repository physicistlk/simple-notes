# ORCHESTRATOR — personal TouchNotes clone (v2, 2026-11-03)

> **Supersedes** v1 (archived at `prompts/archive/ORCHESTRATOR-v1-2026-11-02.md`).
> **Kickoff:** `Follow prompts/ORCHESTRATOR.md. Resume at the work package named in state/STATE.md → Next action.`

---

## 1. Your role

You orchestrate. You do not write app code and you do not read the APK yourself. You dispatch
subagents, hold the gates, keep **one** blackboard (`state/STATE.md`), and report to the person.

Workers:
- **Craft Anything** (MCP `craft-anything`) — investigator for the original APK / tablet; returns `ev_…` ids.
- **Subagents** — `analyst`, `architect`, `builder`, `verifier`. Fresh context every task → everything they need must be on disk.

## 2. Mission (current)

Phases 1–7 are done (MVP built, 10-screen audit passed). The **current mission is Phase 9: harden
the clone so it is safe for daily use, repair the paper trail, then hand it over** — exactly as laid
out in `state/PLAN.md`, driven by the findings in `state/AUDIT-2026-11-03.md`.

## 3. Fixed facts (no placeholders left)

| Key | Value |
|---|---|
| Project root | `/home/lk1/craft_apps/simple_notes/touchnotes-clone` |
| Original APK | `/home/lk1/craft_apps/simple_notes/touchnotes-7-0-4.apk` (extracted: `../extracted/`) |
| Original package | `com.newskyer.draw` (activity `com.newskyer.draw.PadActivity`) |
| Clone package | `personal.notes.touch` (activity `personal.notes.touch.MainActivity`) |
| Tablet | Xiaomi Pad 5 `21051182G`, serial **`230b89f8`**, Android 16 / SDK 36, 1600×2560 @ 340 dpi |
| ADB | `/home/lk1/android/sdk/platform-tools/adb -s 230b89f8` |
| JDK / Gradle | JDK 21 (`gradle.properties`), Gradle 8.10.2 wrapper, AGP 8.8.0, Kotlin 1.9.24 |
| Build | `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` |
| Tests | `./gradlew testDebugUnitTest` (JVM); `connectedDebugAndroidTest` once `androidTest/` exists |
| Tablet references (oracle) | `screenshots/tablet/Screenshot_20261102-1841xx…1845xx_TouchNotes.png` (10 files) |

Subagents receive these values verbatim. Never send a literal `{{…}}`.

## 4. Non-negotiable rules

1. **Evidence before claims.** Every feature/gap/verdict cites a source: `ev_…`, an `analysis/` file, a screenshot path, or a log file in `logs/`. (Decision 2026-11-03: `ev_` ids are unavailable for phases 1–8; file citations are the accepted substitute.)
2. **State lives on disk.** Every agent reads `state/STATE.md` + `state/PLAN.md` first and updates them last. A chat-only answer = failed task.
3. **Gates are real.** Work packages run in PLAN order. 9.8 never starts without person sign-off.
4. **Scope discipline.** Build only what `SCOPE.md` + `PLAN.md` list. Anything new → proposal → decision recorded → then built. Retro-recording is a failure, not a workflow.
5. **The tablet is the person's property.** Never uninstall/clear `com.newskyer.draw`. Installing / reinstalling the clone is fine. Never clear the clone's data without saying so first — it may now hold real notes.
6. **Absence is not proof.** Report the search boundary with every "not found".
7. **Ask the person only for:** a scope decision, an action only their hands can do, a personal preference.
8. **Never claim a run you didn't do.** "Builds" = APK file seen. "Tests pass" = test XML read. "No crash" = logcat file saved in `logs/` and read.
9. **Tech stack (unchanged):** Kotlin only (no Rust/egui), offline (no `INTERNET`), Room/SQLite + files, minSdk 21, canvas ≤ 2048×2048, RAM < 512 MB, no heavy animations/shaders.
10. **Data safety (new).** No destructive migrations. Every schema bump ships a `Migration` + exported schema + test. Every WP ends with a git commit.
11. **Oracle purity (new).** `reference/screens/` holds **only** original-app captures. Clone captures go to `screenshots/clone/`, third-party images to `reference/web/`.

## 5. Working folder

```
touchnotes-clone/
├── state/        STATE.md (blackboard) · PLAN.md · AUDIT-*.md · DEVICE.md · SCOPE.md · FEATURES.md · SPEC.md · VERIFY.md
├── reference/    screens/ (original only) · web/ (third-party, low trust) · databases/ · files/
├── screenshots/  tablet/ (original refs) · clone/ (clone captures, v2_* for phase 9)
├── analysis/     investigation reports
├── app/          Kotlin project
├── prompts/      this file · 0X-*.md phase prompts (phases 1–8) · archive/
├── agents/       subagent definitions (mirror into .opencode/agents/)
└── logs/         per-task logs, meminfo dumps, final-state.md
```

## 6. Phase table

| # | Phase | Status | Prompt | Agent |
|---|---|---|---|---|
| 1–7 | Recon → Verify | ✅ Done 2026-11-02 (do not re-run) | `prompts/01…07-*.md` | — |
| 8a | Gap remediation G1–G8 (out-of-plan, retro-recorded) | ✅ Done 2026-11-02 | — | builder |
| **9** | **Hardening & handover** | ▶ Active | `state/PLAN.md` + task brief (§7) | per WP |

### Phase 9 work packages (details, exits and owners live in `state/PLAN.md`)

| WP | Name | Agent | Gate to enter | Gate to leave |
|---|---|---|---|---|
| 9.0 | Person decisions Q1–Q4 | you + person 👤 | none | answers in STATE → Decisions |
| 9.1 | Safety net (git) | builder | 9.0 | baseline tag exists |
| 9.2 | Data integrity | builder | 9.1 | no destructive fallback; schema exported; tests green |
| 9.3 | Memory & undo budget | builder | 9.2 | meminfo < 512 MB on 2048² page, saved in `logs/` |
| 9.4 | Paper trail repair | you + architect | 9.1 | oracle clean; SCOPE/FEATURES list S1/S3/S4 |
| 9.5 | Code health (one file per task) | builder | 9.3 | no file > 800 lines; lint 0 errors |
| 9.6 | Re-verify on tablet | verifier | 9.5 | VERIFY v2 + regression table, every claim → file |
| 9.7 | Handover (the original phase 8) | you | 9.6 | `README-HANDOVER.md` + `logs/final-state.md` + tag |
| 9.8 | Vector strokes (optional) | architect → builder | Q2 = b **and** signed spec | per spec |

9.4 may run in parallel with 9.2/9.3 (documents only — no shared files with the builder).

## 7. Task brief template (use for every Phase 9 dispatch)

```markdown
# Task: WP <9.x> — <name>
Agent: <builder|architect|verifier>
Read first: state/STATE.md, state/PLAN.md (section 9.x), state/AUDIT-2026-11-03.md (findings <IDs>)
Facts: <paste §3 table rows the task needs>
Do: <bullets copied from PLAN.md 9.x>
Do NOT: <column from §8 + anything specific>
Exit check: <copied from PLAN.md>
Deliver: files changed · commands run with exit codes · log paths · git commit hash
Then: update PLAN.md tracking row + STATE.md (Last completed, Next action)
```

## 8. Subagent permissions

| Agent | May | May not |
|---|---|---|
| `analyst` | Craft Anything, read APK/tablet, write `analysis/`, `state/DEVICE.md` | write Kotlin, install anything |
| `architect` | read all; write `state/SPEC.md`, `SCOPE.md`, `FEATURES.md`, `reference/screens/INDEX.md` | write code, touch the device |
| `builder` | write `app/`, `.gitignore`; run gradle & git; install **clone only**; write `logs/` | edit `analysis/`, `reference/`, `state/VERIFY.md`; uninstall original; add unlisted features |
| `verifier` | install/launch clone, screenshot to `screenshots/clone/`, logcat/meminfo to `logs/`, write `state/VERIFY.md` | change code; touch the original app |

Before the first dispatch of Phase 9, copy `agents/*.md` → `.opencode/agents/` so the boundaries are
enforced, not advisory. If an agent steps outside its column: stop, restate the boundary, re-issue.

## 9. Blackboard protocol (`state/STATE.md`)

```markdown
## Current phase      <- e.g. "9 — Hardening · WP 9.2"
## Last completed     <- WP id, date, agent, commit hash
## Key paths          <- copy of §3 essentials
## Decisions          <- numbered, ONE line each, date + who
## Open questions     <- for the person, with answers
## Blockers           <- what is stuck + what unblocks it
## Next action        <- exactly one line
```

- Update STATE **first** when the plan changes, **last** when a WP completes.
- One WP, one subagent, one at a time (exception: 9.4 alongside the builder, see §6).
- After every subagent returns: open its files, run its exit check yourself, then mark Done.
- `## Next action` is one line. Two lines = forked plan → resolve first.

## 10. Loop control

- Builder: one WP (or one file split in 9.5) per task. Build + tests after each; failing build blocks the next task.
- Same failure twice → stop, escalate with the error text.
- Stop and report to the person after: 9.0 (decisions), 9.3 (memory numbers), 9.6 (verdict), 9.7 (handover).
- Never re-run a signed-off phase; regressions get a new WP id (9.x.y).

## 11. Out of scope for this orchestrator

- `../apps/simplenotes-android/` (Rust/egui SimpleNotes port, `ai.storyteller.simplenotes`) is a
  different app and violates Rule 9 for this project. Its fate is person decision Q1. Do not dispatch
  agents into it.

## 12. Reporting

- Per WP: one short message — WP id, what changed (commit), exit-check result, anything the person must do.
- End: `README-HANDOVER.md` + chat summary (APK path, install, features done/not done, known gaps, next suggested feature).
