# Agent Guide (`AGENTS.md`)

Entry point and operational protocol for autonomous agents in `realme-unlink`.

## Guiding Principle

Only document what an agent **cannot quickly recover by reading the code**.
Code is the single source of truth for *what the code does*. Documentation exists
exclusively for *where things live* (`context/MAP.md`) and *why major tradeoffs
were made* (`context/DECISIONS.md`). Everything else rots.

Reference files in plain `path:line[:column]` format (e.g. `app/src/main/java/app/realme/unlink/MainActivity.kt:51`).

---

## Hard Guardrails

1. **ADB Device Safety:** Never execute `adb` commands or attempt to interact with connected Android hardware unless explicitly instructed by the user and confirmed that the device is free. Another agent or user may be testing simultaneously on the shared hardware.
2. **Hardware Protection:** Never send arbitrary, exploratory, or blind writes to headset EEPROM or DSP registers. Every write command must be validated through safe, reversible isolation with immediate read-back verification and baseline restoration.
3. **Local-Only Boundary:** Zero network calls, zero cloud dependencies, zero account requirements, zero telemetry, and zero `INTERNET` permission in `AndroidManifest.xml`.
4. **Quick Settings Tile Entrypoint:** The app is launched strictly via the Quick Settings tile (`UnlinkTileService`). Do not expose a standard launcher activity icon.
5. **Scoped Changes:** Keep code edits surgical. Verify behavior and run tests locally before documenting or claiming completion.
6. **No Direct Default Branch Commits:** Commit only to working branches or when explicitly requested by the user.

---

## Read Routing

Read these documents before taking action:

- **Before changing structure, components, or data flow:** Read `context/MAP.md`.
- **Before changing an architectural tradeoff, protocol assumption, or default:** Read `context/DECISIONS.md`.
- **While writing code or fixing issues:** Keep `context/CONVENTIONS.md` open.
- **At session start:** Run `todo list` to review active work; run `todo claim <id>` before editing orchestrated tasks.

---

## Event-Based Write Triggers

Update documentation only when triggered by these events:

- **Module / layout / data-flow changes:** Update `context/MAP.md` immediately.
- **Durable architectural or protocol tradeoff made:** Append to `context/DECISIONS.md` only if the change clears the Decision-Log Bar below.
- **New repeatable rule or developer workflow established:** Append to `context/CONVENTIONS.md` as a terse imperative with zero rationale.
- **User-facing setup, build commands, prerequisites, or controls change:** Update `README.md`.
- **Task progress or operational checkpoints:** Track in `.pi/todos/`.

---

## Decision-Log Bar

`context/DECISIONS.md` is a curated Architecture Decision Record (ADR) file, not a changelog, worklog, or implementation journal.

Append an entry only when **all four** conditions are met:

1. The choice changes architecture, public behavior, wire protocol, data shape, dependency ownership, or an irreversible migration path.
2. A future agent is likely to choose a different plausible path without knowing the rationale.
3. The rejected alternative and its tradeoff/cost are non-obvious from reading the code.
4. The decision will remain relevant after the current task is completed.

### Explicit Exclusions

Do **not** record decisions for:

- Bug fixes, patches, or crash repairs.
- Code cleanup, formatting, or dead-code removal.
- Class, method, or variable renames.
- Mechanical refactors within existing architectural boundaries.
- Tactical implementation details inside a single feature.
- Routine test, lint, or build maintenance.
- Status summaries or feature progress lists.

---

## CONVENTIONS vs DECISIONS Boundary

- **`context/CONVENTIONS.md`** contains pure, terse imperatives with **zero rationale** (what to do). If a rule requires "because", the reasoning may be a decision.
- **`context/DECISIONS.md`** contains the context, decision, tradeoff, and status for choices that pass the Decision-Log Bar.

---

## Todos ↔ Decisions Rule

- Working context, live session findings, and task checkpoints belong in `.pi/todos/`.
- Before closing or completing a todo, evaluate if any tradeoff made crosses the Decision-Log Bar. If yes, graduate it to `context/DECISIONS.md`. If no, leave the rationale in the todo body.

---

## Definition of Done

A task is complete only when:

1. Code builds cleanly via `./gradlew compileDebugKotlin` and tests pass via `./gradlew testDebugUnitTest`.
2. Relevant linters pass (`./gradlew :app:lintDebug` and `npx --yes markdownlint-cli2`).
3. Durable docs (`context/MAP.md`, `context/DECISIONS.md`, `context/CONVENTIONS.md`, `README.md`) reflect reality with zero drift.
4. Any architectural choice meeting the Decision-Log Bar is recorded in `context/DECISIONS.md`.
5. Orchestrated todos in `.pi/todos/` are updated or closed.
