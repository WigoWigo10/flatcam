# QA project context — FlatCAM FX

Rest/Connect correction (2026-11-10, after 513cbccb): earlier nominal residual
percentages were NOT confirmed toolpath defects. Closed-ring buffer simplification
underestimated tiny cutter sweeps; access also included ULP-width exact-fit channels.
CutterSweep preserves closed-path endcaps. Reachability retains nominal/raw residuals
and separately reports stable vs precision-sensitive access; never shrink the cutter.
Raster-family Rest (Lines/Combo/REGION_LINES) now selects work from stock while
allowing centers in each original permitted area. Standard/Seed retain legacy policy.
48/48 paired community measurements use the SAME corrected analyzer on both sides,
unchanged targets/diameters, no regressions. Largest stable residual core: 38.1023
mm2 before -> 0.000003764 mm2 after; stable Geometry cores in 32 Rest cases are zero
after the declared tolerance. Max exported stable core 0.000004632 mm2. Raw nominal
and precision-sensitive stock remain visible; not physical full-cleanup certification.
Regional Connect now joins safe straight moves before integrating raster/finishing
in a graph. Only short already-cut boundary sections may be retraced; virtual edges
are lifts. 16/16 actual GRBL exports retain continuous required intervals, reduce
retractions by 10..1455 and estimated minutes by 4.1..25.7% vs ordinary default
NEAREST. Rapid XY increases in 10/16. Test parameters matter; no real machine/FPS
or isolated generation benchmark claim, and the strategy remains experimental.
Full reactor: 2511 cases, 2493 passed, 18 optional skips, no errors/failures. Final
Rest help: 12 FX/i18n checks green. 192 distance controls, 4 Java interval-auditor
guards and 6 Python report guards pass. Frozen-baseline counterproof: 7/7 failures;
final classes: 7/7 pass; isolated first-stage kernel: 12/12 pass.
See flatcam-next/REST_COVERAGE_REFINEMENT.md and REGION_LINES.md, snapshots under
validation/, full evidence in target/clearing-connect-fix-20261110. No version bump
or installer: user requested one combined next Beta. Historical numbers below
must be read with this correction; do not erase the older evidence.

Coverage + region prototype (2026-11-10): 96/96 community cases measured,
including 32 two-cutter Rest/angle/MM-IN controls. Nominal access is computed
from the original allowed area, not a thin Rest remainder. GRBL_11_no_M6 is
exported/parsed per cutter and combined; no sampled deviation beyond rounding.
This is not full cleanup: reachable residual reaches 0.5825% with Contour/no
Rest and 2.7265% with Rest; nonzero residual cores remain beyond the declared
chord/rounding tolerance. See flatcam-next/CLEARING_COVERAGE_VALIDATION.md.
REGION_LINES is opt-in in NCC/Paint. It preserves raster cuts, finishes sampled
cells and keeps SOURCE CNC order/direction; existing explicit settings survive.
32/32 raw edge comparisons preserve cuts. SOURCE/no-Connect XY travel decreases
7.7–91.3%; with Connect, 14/16 worsen XY travel and all increase separate cut
sequences (+1..+543). Do NOT promote this prototype or claim a default CNC,
physical cycle, rendering or FPS win. See flatcam-next/REGION_LINES.md.
Full reactor: 2503 cases, 2485 passed, 18 optional skips, no errors/failures.
Final localized NCC recipe: 12 FX/i18n checks rerun green. Three Python comparator
tests and 96 regional distance controls pass. Snapshots are under validation/.
No version bump/installer: user wants all changes in the next Beta together.

Connect performance (2026-11-10), core commit 61859b6c: JFR found 3872 of 4035
Connect execution samples under within. Prepare the original polygon once and
reuse contains as the converse of sweptConnector.within(originalArea). Preserve
the same buffer radius/resolution, strict max-walk, nearest scan and tie/direction
rules. Fetch each read-only coordinate array once; default JTS array sequences
return their array, so getter counts are NOT an allocation/byte measurement.
No endpoint spatial index or path-ordering change was introduced.
Three alternating fresh-JVM pairs on Horizon Choc, no JFR: median generation
38277.781 -> 6013.878 ms (6.36x in this control). Raw paths/areas/diagnostics
identical. 64/64 community outputs EXACT_MATCH; 96 distance controls pass.
Full verify green: 2477 cases, 2459 passed, 18 optional skips, no errors/failures;
nine Connect controls rerun after final accessor-contract naming changes pass.
Frozen baseline jar still fails exactly two deterministic waste controls;
seven behavior checks pass on both. Three Python comparator controls pass.
See flatcam-next/CONNECT_OPTIMIZATION.md for all evidence/reproduction. JFR
allocation weights are sampled estimates, NOT peak/resident RAM; performance
is generation latency, not FPS or machine cycle time. Small inputs may not gain.
Connect retracing and full swept-coverage limitations remain; no new installer.

Post-Beta 7 Lines refinement (2026-11-09): exact positive collinear raster/Contour
overlap is removed from raster only; closed finishing rings remain. Contour-off
Lines supplements each missed positive cutter-inset component with a clipped,
parallel interior pass, preserving angle and units. Combo keeps Seed/Standard
fallback for an entirely empty raster. Per-tool result diagnostics
separate cutter-inset-empty polygons, supplemented components and reachable
components without a pass. NCC/Paint historical result panels highlight each
category, reset on generation/tool cleanup, and explain that these are NOT full
swept-coverage measurements. Four-language rich/accessibility help and narrow
sidebar/theme checks are present. Footprint buffers/union now run only for Rest;
its existing geometrical oracle retains the original policy. See
flatcam-next/LINES_REFINEMENT.md for red/green/counterproof evidence and limits.
These changes are development source; Beta 7 tag and installers below are intact.
CAM commit 0c3aaf3e; FX UI commit 57cb94f3. The complete reactor executed 2468
cases; one UI test visitor failed on a lawful empty TitledPane. Its null guard
preserves all reference-identity assertions. A subsequent reactor verify with
86 affected cases passed; seven diagnostics cases passed again after the final
diameter-only label change (empty cutters must not invent CNC T numbers).
Final per-class state for this round: 2450 pass, 18 optional skips, no failures
or errors; obsolete LauncherConfigurationTest XML (three older cases) excluded.
Do not describe the earlier integral log as a single uninterrupted green run.
Final 96 distance checks pass. Public matrix generated 64/64: 60 within the
90-second cooperative budget, four Horizon Choc Connect retries took 111-137s
with a 300s budget. Attempts, SHA-256 selection manifest and old results retained.
Dense Connect runtime/retracing and full swept coverage remain follow-ups.
The independent auditor v4 keeps exact missed-component counts separate from
ULP-scale near-boundary passes, requiring a span much longer than the numeric
tolerance; it never changes production paths. Six Python controls pass, and the
new one-ULP test fails with unchanged v3. See LINES_REFINEMENT.md for formula,
counterproof, data directories and what has NOT been physically validated.
Final independent audit: 64 cases; no unserved reachable components beyond its
explicit numerical tolerance, no unexpected interior endpoints at 1% diameter.
Exact one-component ULP misses remain visible in four Horizon Choc configurations.
Repeated-centerline cases above 1e-6 mm: 34 -> 22; all remaining cases have Connect
on. Every community record retains coverageStatus=NOT_MEASURED. Eight-board
raw inputs, 90s failures and 300s retries have a SHA-256 selection manifest.

Beta 7 release (2026-11-09): binary source/local tag v0.1.0-beta.7 =
b64ef4dc435e0334f21f9ccc62670972113b4cdb, clean tree; MSI 0.1.70. Five related CAM defects
fixed in a5a83060, detached Paint listener cleanup refined in b64ef4dc. Editor
context routes preserve one owner/panel/exit/history; Paint validates full source
and optional reference identities separately from selected subsets, epoch and
late cancellation; stale selected polygons are rejected. Fixed-unit Paint
source/reference pickers and explicit localized help prevent 0.3 mm -> 0.3 in
reinterpretation. Nested unit-member polygon collections are selectable.
Observer cleanup on tool replacement prevents old Paint from cancelling the
next tool's placement. No clearing algorithm or dependencies changed.
92 focused tests pass; initial red assertions and first correction failures
preserved. Identical audit: 13 cases x10 pass with fixed classes; unchanged Beta 6
still fails eight cases. Cleanup has separate red/green identical-probe proof.
Full final verify: 2435 cases, 2417 passed, 18 optional skips,
zero failures/errors. First pre-candidate was deliberately interrupted after
cleanup discovery, NOT a pass; final source was fully reverified.
Portable, per-user MSI and delivered Beta 6 MSI 0.1.60 -> 0.1.70 upgrade pass,
including 13 unexcluded CAM safety cases on EACH packaged image, source checks,
GPU/software/JFR and prior CAM/project flows. Additional 96 packaged Lines
distance controls pass with 0.5%-diameter allowance, NOT a coverage/duplication
or physical-CNC proof. Zero family products left installed; EXE/MSI/ZIP hashes
rechecked in Downloads. 3946 entries per language. See release/VALIDACAO-0.1.0-beta.7.md
and target/releases/0.1.0-beta.7-20261109-184101-491. Old Lines findings remain open.
No new benchmark, physical CNC, second PC/VM or EXE wizard click acceptance.
No agent push or tester-message command. Prior Beta artifacts/tags preserved.

Historical Beta 6 CAM audit (2026-11-09), before the fixes above: five confirmed issues in
packaged Beta 6 c28019b7, also present in packaged Beta 5 8e2fcc18. Gerber context
Edit bypasses other-editor guards (Geometry/Excellon/G-code), starts two editors
and loses the original panel route; Paint publishes stale picked polygons after
source removal before/during calculation; Paint MM->IN source switch reinterprets
0.3 mm as 0.3 in, confirmed in generated Geometry (7.62 mm); Paint ignores a cancel
requested after worker completion but before FX publication; polygonsOf drops
unit-member nested GeometryCollections even though Paint core accepts them.
12 UI scenarios x10: eight failed contracts x10, four passing controls x10,
zero final instrument errors. Beta 5: same eight failures in one run each;
angle control explicitly excluded because absent. No known-good baseline or
introducing commit identified. NCC stale/cancel controls pass. Additional 96
synthetic Lines distance cases MM/IN, two cutters, per-tool angles, Rest,
Connect/Contour pass with 0.5%-diameter allowance for arc approximation; NOT
whole-area coverage, duplicate-path, efficiency, FPS or physical CNC proof.
See flatcam-next/VISTORIA_CAM_BETA6.md and tools/audit-cam-related.ps1. Raw
evidence target/cam-related-audit-20261109-*. No app code/MSI/tag changed;
do not call editor guards complete or Paint state lifecycle fully protected.

Beta 6 release (2026-11-09): implementation 9e87ea55, binary source/tag
v0.1.0-beta.6 = c28019b72b16ee65534c62ba9f7e4fdf02814f95, clean tree;
app 0.1.0-beta.6 revision 0, MSI 0.1.60. Full verify: 2406 cases, 2388 passed,
18 optional skips, zero failures/errors, 5:07. Packaged portable, per-user MSI
install/uninstall and delivered Beta 5 MSI 0.1.50 -> 0.1.60 upgrade PASS.
All three probes exercise four editor navigation/cancel routes, NCC explicit
Lines angle + native draft reopen and Paint angle through actual raster segments,
alongside prior workflows (23 objects, five programs, 8 panel drills/4 slots).
Bundled GPU/software/UTF-8/JFR probes pass; external project preserved by SHA-256;
zero registered family products after tests. EXE/MSI/ZIP copied to Downloads,
hashes rechecked. See flatcam-next/release/VALIDACAO-0.1.0-beta.6.md and candidate
target/releases/0.1.0-beta.6-20261109-153800-491, including selected packaged logs.
No physical CNC, EXE wizard clicks, second PC, new benchmark or full public-board
coverage. Raw Lines defects below remain open. No agent push command; observed
fork/flatcam-next ref reached c28019b7 with reflog 'update by push'.

Raw Lines audit (2026-11-09): 64 cases on all eight community boards, NCC/Paint,
Connect/Contour, one 0.4 mm cutter. 34 cases have repeated centerlines >1e-7 mm;
max 746.7692 mm / 3.6831% in Horizon MX NCC. No unexpected interior open endpoints
within 1%-diameter tolerance. Community full-area coverage NOT measured; explicit
NOT_MEASURED in final report. Synthetic raster/Contour overlap 35.2 mm occurs
in FX and original Python Lines kernel; survives one-pass portable G-code,
read by original Python parser (~35.21 mm). Thirteen reachable polygons in
breadboard board get no output with Contour off, produce output with Contour on;
same original Python kernel control reproduces. Do not call all breaks bugs or
all recutting waste: clearance breaks, stepover overlap, deliberate finishing
and retravel to reduce lifts have different purposes. Three helper tests pass.
No production change here. See flatcam-next/LINES_RAW_PATH_AUDIT.md and ignored
target/lines-path-audit-20261109; spatial unions hide execution multiplicity.

Public community corpus added locally (2026-11-09): samples/community/README.md,
eight packages from seven projects, 90 fabrication files including two gbrjob
metadata files, 88 production FX parser imports passed, 174 downloaded/extracted
hashes rechecked. Raw data/gallery/reports are gitignored; manifest pins commits
and original blob hashes. Not a Python or CAM/machine validation. A single-sided
power board lacks published drills; nine capacitive-board supplier intermediates
fail the FX parser on a bare '*' before %FS and are preserved as negative cases.
The final capacitive manufacturing corpus uses the unmodified nested KiCad ZIP.
Keep original license scopes (hardware CERN vs firmware MIT); audio's MIT is
declared in its README, no standalone upstream LICENSE. No production fix here.

Earlier implementation validation (2026-11-09, subsequently included in Beta 6): editor navigation trap
reproduced in Geometry/Gerber/Excellon/G-code; unrelated tools now preserve and
reveal the active editor until explicit completion/discard. Original MainWindow
counterproof reproduced 40/40; restored fix passed 40/40. NCC and Paint now have
opt-in per-tool raster angle, with automatic legacy behavior unchanged. Full
verify: 2406 cases, 2388 passed, 18 optional skips, zero failures/errors, 4:45.
Four-language/tooltips audit and narrow four-theme angle controls passed.
Lines pad coverage guards pass in both public generators, but the exact tester
report of broken Lines was NOT reproduced; no private project/parameters supplied.
Do not claim an established coverage bug fixed. See
flatcam-next/LINES_AND_EDITOR_REVIEW.md for evidence and numerical limits.
Original Beta 5 installers in Downloads remain unchanged; that implementation
was subsequently included in the Beta 6 release recorded above.
Follow-up rounded-pad comparison runs the original Python NCC GUI initializer
and Paint clearing kernel on public shared pads: 16 controls, 8 MATCH_SAMPLED,
8 DIFFERENT, zero oracle errors. All Connect-off cases match; all Connect-on
cases differ spatially despite nearly identical Contour-on coverage. Do NOT
count this as full parity. No production algorithm changes in the follow-up.
Images/data: flatcam-next/target/rounded-pads-20261109. Initial asymmetric raw/union
comparison is preserved and superseded by symmetric existing spatial-union
policy, with two helper tests. See the review's follow-up section for limits.

Latest release evidence (2026-11-05): Beta 5 source
8e2fcc186c20b5934927981e336fd1753447f552, clean tree, local annotated tag
v0.1.0-beta.5. Full verify: 2374 cases, 2356 passed, 18 optional skips, zero
failures/errors. Portable, clean per-user MSI install/uninstall and delivered
Beta 4 r1 (0.1.41) -> Beta 5 (0.1.50) upgrade passed. Packaged probes verify
internal cutout preview/validation/CNC/project reopening, four language catalogs
and SVG flags alongside existing CAM/editor/Excellon checks. Each UI smoke has
22 objects and five programs. External project SHA preserved; no registered
family products left behind. EXE/MSI/ZIP/hashes in Downloads. No physical CNC,
second PC/VM, new benchmark or EXE wizard click test. Agent did not invoke push;
remote-tracking fork branch was observed at the binary commit with an update-by-push reflog. See
flatcam-next/release/VALIDACAO-0.1.0-beta.5.md; later documentation commit does
not change binary source identity. Prior Windows temporary-cleanup error did
not recur in this candidate's complete verify. Skill runner qa.mjs not present;
used established Maven and packaged-runtime probes instead.

Latest release evidence (2026-11-01): Beta 4 revision 1 source 0c14179b,
clean binary source, local tag v0.1.0-beta.4-r1. Full verify: 2343 cases,
2325 passed, 18 optional skips, zero failures/errors. Excellon properties
diameter editing, Plot publication, undo/redo and native/Python persistence
passed on portable, clean MSI and upgraded installations. Delivered Beta 3
(0.1.3) and original Beta 4 (0.1.4) both upgraded to MSI 0.1.41, with external
projects preserved and no registered family installations left afterwards.
Original Beta 4 artifacts/tag preserved; r1 MSI/EXE/ZIP/hashes in Downloads.
No push, physical CNC, second-PC/VM, new benchmark or EXE wizard click test.
See flatcam-next/release/VALIDACAO-0.1.0-beta.4-r1.md for exact evidence.

Release evidence (2026-11-01): Beta 4 source 6b45a634, clean tree, local tag
v0.1.0-beta.4. Full verify: 2334 cases, 2316 passed, 18 optional skips, zero
failures/errors. Built MSI/EXE/ZIP with Temurin 25.0.5+8; extracted/installed
JAR probes passed main CAM flows, Standard/Machinist Z in three panels MM/IN,
original/copy G-code saves, Cancel preserving saved files and CAM Undo/Redo.
Clean per-user MSI install/uninstall and actual delivered Beta 3 -> 4 upgrade
passed with an unchanged external test project. No installed family left behind.
Artifacts/hashes copied to Downloads. No push, physical CNC, new benchmark,
EXE-wizard clicks or automatic downgrade validation. See
flatcam-next/release/VALIDACAO-0.1.0-beta.4.md for exact candidate and evidence;
later documentation commits do not replace the binary source identity.

Native splash DPI incident (2026-10-27): java.exe-hosted AWT checks missed the
JNI exe's DPI-unaware initial context. Reproduced physical HWND shrink from
1240x600 at (820,552) to 620x300 at (410,276) when Glass starts on the 200%
screen, while AWT getters stayed unchanged. Embedded PerMonitorV2 manifest
fixes the context before JNI/AWT. NativeSplashTransitionTest compiles the host
with an isolated Maven runtime and own-process physical Win32 observer. Red,
green, manifest-omitted red, restored green; ten stable native repeats and
four hardware/software + 200%/100% combinations. Full verify: 2115 passed,
18 optional skips, zero failures/errors (2133 records). See
flatcam-next/STARTUP_SPLASH_DPI.md. Build now needs windres beside g++.

Observed 2026-10-23. Sources: `flatcam-next/pom.xml`, module `src/test`
directories, `CNC_PARAMETER_PARITY.md`, `MARLIN_PARITY.md`, and recorded Maven
logs under `flatcam-next/target`. This is a desktop app, not a Next.js website.

## Product

Latest serial implementation (2026-10-27): Geometry Jump/drag rotation committed
as f87635e0 (2032 records, 2014 passed, 18 skips); next increment adds G91 and
sampled circular Excellon routes, preserving their full path through CAM/CNC.
Final verify: 2048 records, 2030 passed, 18 optional skips, zero failures/errors.
Native projects with routed paths use version 3 so old v2 readers refuse them;
unchanged straight-slot projects still write v2. Python/G85 export refuses
curved routes. Original Python reader probe: one linear match, one G91 difference,
two unsupported curves, not parity. See flatcam-next/EXCELLON_ROUTING.md.
These new gestures/routes were tested programmatically, not in a fresh GUI session.

Earlier real-window review (2026-10-27): full verify 2026 records, 2008 passed,
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
