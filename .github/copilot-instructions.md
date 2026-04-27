<!--
SPDX-FileCopyrightText: 2026 The Authors
SPDX-License-Identifier: Apache-2.0
-->

# Copilot Instructions

## Project-Specific Rules

### 1. Copyright Headers

- Every **new file** must include an SPDX copyright header for the current year and "The Authors":
  ```
  SPDX-FileCopyrightText: <current year> The Authors
  SPDX-License-Identifier: Apache-2.0
  ```
  Use the appropriate comment syntax for the file type (e.g. `//` for Kotlin, `#` for shell/TOML, `<!--`/`-->` for XML/HTML).
- If a file is **completely rewritten** (no original, non-boilerplate content remains), remove the original copyright line and replace it with the "The Authors" copyright for the current year.
- Do **not** modify copyright headers in files you are not otherwise changing.

### 2. UI Strings Must Come from Resources

- Every string displayed in the UI must be defined in `res/values/strings.xml` (or an appropriate locale variant) and referenced via `R.string.*` / `@string/*`.
- Never hardcode user-visible strings directly in Kotlin/Java source or layout XML.

### 3. Atomic Commits

- Each commit must represent a single, self-contained change. Do not bundle unrelated work (sidequests) into the same commit.
- If you notice an unrelated improvement while working on a task, do it in a **separate commit** so that each change can be reviewed and reverted independently.

### 4. Remove Orphaned Files

- After making changes, check whether any files, classes, resources, or imports have become unused as a direct result of your changes.
- Remove orphans that **your changes** created. Do not remove pre-existing dead code unless that is the explicit goal of the task.

### 5. Do Not Review Generated Files

- Treat Gradle wrapper files as generated/third-party — do not suggest changes to them:
  - `gradlew`
  - `gradlew.bat`
  - `gradle/wrapper/gradle-wrapper.jar`
  - `gradle/wrapper/gradle-wrapper.properties`

---

## General LLM Coding Guidelines

*(Derived from [Andrej Karpathy's observations](https://x.com/karpathy/status/2015883857489522876) on common LLM coding pitfalls.)*

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

### Think Before Coding

Don't assume. Don't hide confusion. Surface tradeoffs.

Before implementing:

- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### Simplicity First

Minimum code that solves the problem. Nothing speculative.

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### Surgical Changes

Touch only what you must. Clean up only your own mess.

When editing existing code:

- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it — don't delete it.

When your changes create orphans:

- Remove imports/variables/functions that **your changes** made unused.
- Don't remove pre-existing dead code unless asked.

The test: every changed line should trace directly to the user's request.

### Goal-Driven Execution

Define success criteria. Loop until verified.

Transform tasks into verifiable goals:

- "Add validation" → "Write tests for invalid inputs, then make them pass."
- "Fix the bug" → "Write a test that reproduces it, then make it pass."
- "Refactor X" → "Ensure tests pass before and after."

For multi-step tasks, state a brief plan:

```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.
