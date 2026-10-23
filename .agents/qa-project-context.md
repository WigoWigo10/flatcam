# QA project context — FlatCAM FX

Observed 2026-10-23. Sources: `flatcam-next/pom.xml`, module `src/test`
directories, `CNC_PARAMETER_PARITY.md`, `MARLIN_PARITY.md`, and recorded Maven
logs under `flatcam-next/target`. This is a desktop app, not a Next.js website.

## Product

Java desktop CAM port of FlatCAM Python. Critical evidenced flows: open Gerber
and Excellon; panelize aligned layers; Isolation/NCC/Cutout to Geometry;
Geometry/Drilling to CNC; review/export G-code; save/reopen native and compatible
Python projects. No web production/staging URL applies to these desktop flows.

## Tech Stack

Maven multi-module reactor `flatcam-next`: CAM core, application/project layer,
JavaFX frontend, test support. Compiler release 25, JavaFX 25.0.4 configured in
parent POM. Geometry uses JTS; Python legacy oracle uses original application
classes and Shapely/GEOS. No server database/hosting is involved in this scope.

## Test Stack

JUnit BOM 6.1.3 and Surefire 3.5.4 configured in parent POM. Tests live in each
module's `src/test/java`. Maven logs demonstrate actual execution, not just
configuration. JavaFX integration tests use an FX-thread harness; some are
Windows-only. Python unittest auxiliary readers and original-generator
comparisons live in `flatcam-next/tools`. Oracle environment observed: Python
3.11.1, Shapely 1.8.5.post1, GEOS 3.10.3, OR-Tools 9.10.4067. No browser test
framework is required for this JavaFX task. Resolved Java test dependency
versions should be checked in Surefire XML/classpath when relevant.

## CI/CD

No root `.github` directory was found during this inspection. Other hosted CI,
release gates and deployment automation are unknown; do not infer they exist.
Local gate: run `flatcam-next/mvnw.cmd verify` from the reactor directory.
Surefire XML and redirected build/comparison logs are local evidence in target.

## Environments

Observed execution on Windows/PowerShell. No real CNC controller connected or
validated. Public/synthetic fixtures are preferred. Legacy live-app tests must
isolate settings/offscreen UI and must not modify private projects/preferences.

## Quality Goals

Established task requirement: keep strict CNC comparisons honest, preserve
source files on failure/cancellation, and verify relevant regressions plus
full Maven build. Global coverage/flakiness/time budgets are unknown, not agreed
numeric goals. Last committed baseline `09486037`: 1770 Java passes
(including explicit regression repetitions), 18 optional skips, 48 Python
auxiliary passes (logs referenced in LINE_XYZ_PARITY.md). The uncommitted ICP
investigation is recorded in ISEL_ICP_PARITY.md; distinguish unsupported
reference changes from actual legacy crashes, and never count either as parity.

## Risk Areas

- CNC units/dwell/feed/spindle/controller semantics: errors can change physical
  machine behavior. Evidence: MARLIN_PARITY.md and ISEL dwell investigation.
- Python/native project roundtrips: data loss risks evidenced in
  CNC_SIX_STEP_REVIEW.md; `.FlatPrj` has explicit restrictions.
- Worker cancellation/stale publication: existing MainCamFlow and export tests
  protect user edits and files. Physical safety is not certified by a preview.
- Windows temporary cleanup: the line_xyz full verify hit a JUnit
  DirectoryNotEmptyException after a passing Tcl save test; the directory was
  absent later and the isolated test passed. Root cause is not confirmed.
  Preserve failing logs and do not silently relax cleanup assertions/retries.
Incident rates, quantitative likelihood and risk scores are unknown.

## Team

Headcount, QA specializations and dev:QA ratio are unknown. Work in this session
is serial as requested; do not infer authority for sub-agents or remote pushes.

## Conventions

Java tests use `*Test.java`, JUnit assertions, synthetic geometry and `@TempDir`.
JavaFX tests use IDs/reflection and `TerminalPanelTest.fx` where appropriate.
Python reader tests use unittest with no live machine/network dependency.
Run `.\mvnw.cmd` from `flatcam-next`; on PowerShell prefer `cmd /c` for Maven
log redirection to avoid misleading native STDERR failures. Keep evidence under
target, use fresh comparison output directories, preserve strict status/tolerance.
Commits require user authorization; no push without authorization.
