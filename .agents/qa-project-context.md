# QA project context — FlatCAM FX

Observed 2026-10-23. Sources: `flatcam-next/pom.xml`, module `src/test`
directories, `CNC_PARAMETER_PARITY.md`, `MARLIN_PARITY.md`, and recorded Maven
logs under `flatcam-next/target`. This is a desktop app, not a Next.js website.

## Product

Latest serial review (2026-10-27): full verify 2026 records, 2008 passed,
18 optional skips, zero failures/errors. Real FX GUI exercised four themes,
new Excellon array preview/confirm/undo/redo/cancel and import fields on a copy
of the 21-object project. Python GUI loaded/rendered its copy, but window
activation failed after recovery; interactive Qt comparison and DPI/multiple
monitors remain unverified. Originals/copies SHA-256 unchanged. See
`flatcam-next/UI_PARITY_REVIEW.md`; repeatable isolation helpers under
`flatcam-next/tools/ui-review`. Review does not establish frame-rate gains.

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

## Latest runtime finding — 2026-10-27

The unexplained End Z assertion is now isolated: a standalone HotSpot 25.0.4.1
AVX2 probe returns false for an ASCII substring inside the correct UTF16 text.
10/10 red without workaround, 10/10 green disabling only _indexOfUL. All other
AVX/CompactStrings/GPU paths remain enabled. CNC assertion unchanged; runtime
guard red-before/green-after. Full verify: 1989 passes, 18 optional skips,
zero failures/errors, 2007 records. See flatcam-next/JVM_STRING_SEARCH.md.
Do not describe the prior failure as unconfirmed CNC rounding or random flakiness.
User authorized four serial stages with one commit each; no remote push requested.

## Quality Goals

Continuation after `79f2aedd`: NCC point-touch preparation fix completed,
see `flatcam-next/NCC_POINT_TOUCH_REVIEW.md`. Root cause established:
copper, boundary and raw subtraction exact; extra whole-area buffer(0) moves
ring seams. GUI Rest uses raw subtraction; non-Rest cleans each polygon
separately. Production now follows those two policies; Paint unchanged.
Public diamond regression was red before each fix; 8/8 original GUI controls
MM/IN now pass strict (exit codes recorded), 63 focused Java tests pass.
Same compiled regression is red 10/10 with isolated `79f2aedd` class and green
10/10 with current classes. Real B_Cu 3x1 Rest+Itself+Connect isolated now
passes both tools: sampled distances 5.68e-14 and 1.07e-14 mm. This isolated
export predates only the non-Rest refinement. That first full matrix was
interrupted after finding the single-tool comparator shortcut also buffered
the whole area; it is INCOMPLETE, not a final pass. `legacy_paths` now adapts
single-tool metadata into the ORIGINAL GUI initializer too; no legacy source
or coordinates patched. Public regression/counterproof red/green 10/10,
28 comparator tests pass. The apparent B_Cu 2x2 non-Rest Connect difference
is 5.68e-14 mm under the real GUI handler. Final validation is now under
`target/ncc-seams-final-validation-20261024`, reusing completed final-code
2x2/public exports and reexporting 3x1/advanced. COMPLETE: 33 reports, 203
controls, 201 MATCH_SAMPLED, zero CAM/G-code/partial differences, 2 original
Offset ORACLE_ERROR (one each face); strict-equivalent exit 1, never count
those errors as parity. All NCC controls use original GUI code now. All
three FX flows passed, drill registration/native reopen checked, original
source hash unchanged. Summary includes FX export hashes and consistent
legacy source hashes. All 148 public controls pass; separate public seam
probe passes 8/8. Full verify rerun passed 1868 tests,
18 optional skips. Initial full verify had an unexplained End Z assertion
failure in CncNativePrecisionTest; isolated, packaged and full reruns pass.
Only its failure diagnostic improved; preserve first log, do not invent a
cause. Offset error is proven in the handler/harness, not a complete live
GUI import proof. Next investigate that source representation/load path;
do not reopen the fixed 1 mm Connect gap without new contrary evidence.
All private geometry remains ignored. Consolidated in the subsequent NCC
fix commit; see git log. No push.

Historical baseline before this fix: three-step work committed as `79f2aedd`
on `flatcam-next`, no push: GRBL_11_NO_M6 and
GRBL_11 unchanged at 49/19/34/2/0 each; known differences adjudicated in
GRBL_PATH_DECISIONS.md, not generic pending FX fixes. Native precision review
uses actual codecs and limited coordinate samples; it must not claim a full
UI-thread geometry scan or mechanical accuracy. Full verify: 1867 passes,
18 optional skips, 1885 current-run records, zero failures/errors. Matrix:
one real source/3 layouts plus public MM/IN, 200 MATCH_SAMPLED, 1 DIFFERENT,
2 ORACLE_ERROR, strict exit 1. All FX flows/registration/native reopen passed,
source hash unchanged. Concrete gap: B_Cu 3x1 zero gap, NCC Rest+Itself+Connect,
1 mm tool, sampled distance .218295 mm; clearing area equal, .2 mm tool agrees.
Isolated repro repeats; do not declare intentional/equivalent based only on
high footprint IoU. Original Offset handler fails at ToolNCC.py:1934 with
nested MultiPolygon in both faces. See MAIN_FLOW_MATRIX.md and private target
repros, never commit board geometry. Matrix G-code check parses FX code with
Python; it is not two independent G-code generations. MatrixComplete and
planned/recorded runs distinguish partial summaries; completion does not
override failures or strict results. Start next investigation at this new
Connect case, not the already-adjudicated generic GRBL list.

Following local laser-group implementation on `c8ae73cf`: 1861 Java passes,
18 optional skips (1879 current-run records), zero failures/errors; Python
discovery 151 passes. See `flatcam-next/LASER_GROUPS.md`. Feed/power per group
and focus for Z_laser/Marlin now supported; GRBL_laser simple rejects differing
focus. Common off-power transition clearance uses active groups only; final Z
defaults to last nonempty group. Native/legacy persistence and join_geometry
tested. 32 comparisons/profile: no FX parameter issues, strict still fails;
GRBL milling control unchanged 49/19/34/2/0. Exposure groups must not appear
as fabricated hardware tool selections in diagnostic reports. Inactive
mechanical-change defaults from Python laser files are not requested motions;
active change settings remain preserved/rejected. Manual window/machine check
not performed. This stage is committed locally on flatcam-next, without push.

Latest local stage, 2026-10-24, on `ef1cefdb`: native Roland/HPGL readers,
static Mach3 probe contract and laser End Z/XY support. See
`flatcam-next/NATIVE_PROBE_LASER_PARITY.md`. Full final verify: 1854 passes,
18 optional skips, zero failures/errors (1872 current-run XML records;
exclude 3 stale LauncherConfigurationTest records). Python discovery 150
passes. Public main-flow MM/IN: 27 matches + deliberate stable Seed difference
each; strict still fails. Native HPGL cut signatures must retain PU/PD paths
at zero Z; after opaque M6, probe reader must invalidate both spindle state
and feed. Both local harness defects have red/green/revert/restore evidence.
No contact coordinates, physical G92 frame or macro execution may be invented.

Established task requirement: keep strict CNC comparisons honest, preserve
source files on failure/cancellation, and verify relevant regressions plus
full Maven build. Global coverage/flakiness/time budgets are unknown, not agreed
numeric goals. Prior baseline `a0f19c06`: 1829 Java passes
(including explicit regression repetitions), 18 optional skips, 62 selected
Python auxiliary passes. Repetier/Berta work: REPETIER_BERTA_PARITY.md;
Repetier/Berta verify: 1840 passes / 18 optional skips, Python discovery 119 passes.
Following laser work: LASER_PARITY.md; both stages consolidated in the
2026-10-24 commit (see git log), no push. Full verify 1851 passes / 18
optional skips (1869 records), Python discovery 134 passes. Laser emitter now
actually consults usesG94() and adds final Marlin parking M400. Independent
laser reader observes powered nonnegative-Z XY paths, raw S/FAN duty, focus,
ordered/repeated exposure and barriers; not machine/beam simulation. Nested
legacy header comments remain diagnostic limitations, not certified syntax.
Repetier/Marlin G4 P is milliseconds, FAN duty is not RPM, host pause is only
an operator request. Berta macro effects/work offsets are unknown and block
execution equivalence. Distinguish opaque tool selection/reference changes from
actual legacy crashes. The live probe must pass empty XY as "", not None;
the original entry point treats them differently. Never count errors as parity.

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
