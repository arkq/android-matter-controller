<!--
SPDX-FileCopyrightText: 2026 The Authors
SPDX-License-Identifier: Apache-2.0
-->

# Copilot Instructions

## General Rules

### 1. Simplicity First

- Minimal change that solves the problem. Nothing speculative.
- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- Prefer standard library functions over custom logic.
- If you write 200 lines and it could be 50, rewrite it.

### 2. UI Strings Must Come from Resources

- Every string displayed in the UI must be defined in `res/values/strings.xml`
  and referenced via `R.string.*` / `@string/*`.

- Never hard-code user-visible strings directly in Kotlin/Java source or layout
  XML.

### 3. Remove Orphaned Files

- After making changes, check whether any files, classes, resources, or imports
  have become unused as a direct result of your changes.

- Remove orphans that **your changes** created. Do not remove preexisting dead
  code unless that is the explicit goal of the task.

### 4. Copyright Headers

- Every **new file** must include an SPDX copyright header for the current
  year and "The Authors":

  ```text
  SPDX-FileCopyrightText: <current year> The Authors
  SPDX-License-Identifier: Apache-2.0
  ```

  Use the appropriate comment syntax for the file type (e.g. `//` for Kotlin,
  `#` for shell/TOML).

- If a file is **completely rewritten** (i.e., no original content remains),
  remove the original copyright line and replace it with the "The Authors"
  copyright for the current year. This applies even if common elements like
  imports or basic syntax remain, provided all original logic has been replaced.

- Do **not** modify copyright headers in files you are not otherwise changing.

## Version Control System Rules

### 1. Atomic Commits

- Each commit must represent a single, self-contained change. Do not bundle
  unrelated work (side quests) into the same commit.

- If you notice an unrelated improvement while working on a task, do it in a
  **separate commit** so that each change can be reviewed and reverted
  independently.

## Review Rules

### 1. Do Not Review Generated Files

- The Gradle wrapper files are generated - do not suggest changes to them:

  - `gradle/wrapper/gradle-wrapper.jar`
  - `gradle/wrapper/gradle-wrapper.properties`
  - `gradlew.bat`
  - `gradlew`
