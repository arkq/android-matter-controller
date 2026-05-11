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

### 3. No Monster Files and God Classes

- Each file must contain a **single, well-defined responsibility**. No
  monster files that bundle unrelated composables, classes, or logic.

- Concretely for Compose UI:

  - Each Matter **cluster control** lives in its own file
    (e.g. `OnOffCluster.kt`, `LevelCluster.kt`).
  - Each **device-type control** (a cluster aggregate tailored to a specific
    device type) lives in its own file (e.g. `DimmableDevice.kt`).
  - The **screen/route** file only orchestrates navigation, ViewModel wiring,
    and top-level layout; it delegates rendering to the files above.

- If a file grows beyond a single responsibility, split it before adding more
  code.

### 4. Remove Orphaned Files

- After making changes, check whether any files, classes, resources, or imports
  have become unused as a direct result of your changes.

- Remove orphans that **your changes** created. Do not remove preexisting dead
  code unless that is the explicit goal of the task.

### 5. Copyright Headers

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

## User Interface Rules

### 1. Icons Must Use Material Icons

- Always use icons from `androidx.compose.material.icons` (e.g. `Icons.Filled.*`,
  `Icons.Outlined.*`, `Icons.AutoMirrored.*`) rather than bundling custom
  drawable XML.
- Add a custom drawable only when no suitable icon exists anywhere in the
  Core Material Icons library.

### 2. Unified Loading Indicator

- When a screen is loading primary content, use the `LoadingIndicator` common
  composable to show a unified loading state. The text should be a short status
  message fetched from `res/values/strings.xml`.
- Loading indicator should be displayed only when showing the screen for the
  first time or when explicitly refreshing content. Do not show the loading
  indicator when going back or when screen orientation changes. In those cases,
  the previously loaded content should be displayed and the state should be
  refreshed in the background with in-place updates.
- If there are multiple independent loading states on the same screen, gate
  them behind a single unified loading state. Show the screen content only when
  all loading states are complete - do not show partial content.

## Version Control System Rules

### 1. Atomic Commits

- Each commit must represent a single, self-contained change. Do not bundle
  unrelated work (side quests) into the same commit.

- If you notice an unrelated improvement while working on a task, do it in a
  **separate commit** so that each change can be reviewed and reverted
  independently.

### 2. Format Files Before Committing

- Before every commit, run the project standard `ktfmt` formatter on all Kotlin
  files changed in that commit, then verify no formatting diff remains. Use the
  module task used by CI, for example :app:ktfmtFormat.

## Review Rules

### 1. Do Not Review Generated Files

- The Gradle wrapper files are generated - do not suggest changes to them:

  - `gradle/wrapper/gradle-wrapper.jar`
  - `gradle/wrapper/gradle-wrapper.properties`
  - `gradlew.bat`
  - `gradlew`
