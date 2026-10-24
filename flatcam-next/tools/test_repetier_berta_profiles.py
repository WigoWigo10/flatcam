"""Controller-specific observations must not turn FAN duty or opaque macros into RPM/parity."""
import unittest
from compare_cnc_python import read_program, audit_parameters, equivalent, machining_signature, UnmodeledToolSelection


REPETIER = ("G21\nG90\nM107\nG0 Z15 F900\nM400\nM84\n"
    "@pause Troque para T1 - diametro 1.0000 MM - e continue\n"
    "G90\nG0 Z15 F900\nG0 Z3 F900\nM106 S128\nG4 P250\n"
    "G0 X10 Y0 F900\nG1 Z-1 F50\nM107\nG0 Z5 F900\n")
BERTA = ("G21\nG90\nG17\nG94\nG91.1\nG64 P0.03\nM110\nG54\nM5\n"
    "G0 Z15\nT1\nM6\nM0\nG90\nG0 Z15\nG0 Z3\nM3 S10000\n"
    "G4 P0.25\nG0 X10 Y0\nG1 Z-1 F50\nM5\nG0 Z5\nM111\nM30\n")


def source(profile):
    return dict(profile=profile, units="MM", operation="drilling",
        tools=[dict(id=1, diameter=1, points=[[10,0]], parameters=dict(safeZ=3, depth=1,
            multiDepth=False, depthPerPass=1, feedZ=50, rpm=128 if profile == "Repetier" else 10000,
            offsetZ=0, dwell=True, dwellSeconds=.25))],
        job=dict(toolChange=True, toolChangeZ=15, startZ=None, endZ=5, endX=None, endY=None,
                 changeX=None, changeY=None, rapidFeed=900))


class RepetierBertaTest(unittest.TestCase):
    def test_fan_duty_is_not_spindle_rpm_and_host_tool_is_only_an_operator_request(self):
        parsed = read_program(REPETIER, "Repetier")
        self.assertEqual([], audit_parameters(source("Repetier"), parsed, implementation="fx"))
        cut = parsed["cuts"][0]
        self.assertEqual(128, cut["fanDuty"])
        self.assertIsNone(cut["rpm"])
        self.assertIsNone(cut["spindle"])
        self.assertEqual(1, cut["tool"])
        pause = next(e for e in parsed["events"] if e["kind"] == "hostPause")
        self.assertEqual("operator-request-not-hardware-confirmation", pause["selection"])
        self.assertEqual(1, len(parsed["repetierControls"]["motorDisables"]))
        self.assertEqual(0, parsed["repetierControls"]["finalFanDuty"])

    def test_default_fan_command_is_full_on_not_zero_or_requested_rpm(self):
        parsed = read_program(REPETIER.replace("M106 S128", "M106"), "Repetier")
        self.assertEqual(255, parsed["cuts"][0]["fanDuty"])
        self.assertTrue(audit_parameters(source("Repetier"), parsed))
        for bad in ("M106 S256", "M106 S-1", "M106 S12.5", "M107 S1", "M106 P1 S128"):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                read_program(REPETIER.replace("M106 S128", bad), "Repetier")

    def test_motor_disable_missing_stop_wrong_feed_and_unsupported_g94_remain_visible(self):
        for old,new in (("M84\n", ""), ("M107\n", ""), ("F900", "F800"), ("G90\n", "G90\nG94\n")):
            changed = read_program(REPETIER.replace(old,new), "Repetier")
            self.assertTrue(audit_parameters(source("Repetier"), changed), (old,new))

    def test_comments_do_not_select_tools_and_unknown_host_commands_fail_closed(self):
        for directive in ("; Ferramenta T1", "@pause Take a break", "@pause Change to tool T1 with Tool Dia = 1\n@home",
                          "@pause Troque para T1 - diametro 1 MM T2"):
            program = REPETIER.replace("@pause Troque para T1 - diametro 1.0000 MM - e continue", directive)
            with self.assertRaises(ValueError): read_program(program, "Repetier")
        with self.assertRaises(ValueError): read_program(REPETIER.replace("M84", "T1\nM84"), "Repetier")
        for profile in ("GRBL_11_no_M6", "Berta_CNC"):
            with self.assertRaises(ValueError): read_program(REPETIER, profile)

    def test_legacy_host_instructions_are_recorded_but_not_guessed_from_arbitrary_text(self):
        original = "@pause Troque para T1 - diametro 1.0000 MM - e continue"
        for instruction in ("@pause Change to tool T1 with Tool Dia = 1.0000",
                "@pause Change to Tool Dia = 1.0000, Total drills for tool T1 = 1"):
            parsed = read_program(REPETIER.replace(original,instruction), "Repetier")
            self.assertEqual([instruction], parsed["repetierControls"]["hostInstructions"])
            self.assertTrue(equivalent(parsed["cuts"], read_program(REPETIER, "Repetier")["cuts"]))

    def test_repetier_and_marlin_dwell_are_milliseconds_in_both_length_units(self):
        for profile in ("Repetier", "Marlin"):
            for units in ("G20", "G21"):
                program = REPETIER if profile == "Repetier" else BERTA.replace(
                    "G94\nG91.1\nG64 P0.03\nM110\nG54\n", "").replace("M111\nM30\n", "M400\n").replace("G4 P0.25", "G4 P250")
                program = program.replace("G21", units)
                parsed = read_program(program, profile)
                self.assertEqual(.25, next(e["seconds"] for e in parsed["events"] if e["kind"] == "dwell"))
                wrong = read_program(program.replace("G4 P250", "G4 P0.25"), profile)
                self.assertEqual(0, next(e["seconds"] for e in wrong["events"] if e["kind"] == "dwell"))
                self.assertFalse(equivalent(machining_signature(parsed), machining_signature(wrong)))
                truncated = read_program(program.replace("G4 P250", "G4 P250.9"), profile)
                self.assertEqual(.25, next(e["seconds"] for e in truncated["events"] if e["kind"] == "dwell"))
                with self.assertRaises(ValueError): read_program(program.replace("G4 P250", "G4 P4294967296"), profile)

    def test_berta_macros_cannot_be_treated_as_known_noops_or_full_execution_parity(self):
        parsed = read_program(BERTA, "Berta_CNC")
        self.assertEqual([110,111], [e["code"] for e in parsed["bertaControls"]["macros"]])
        issues = audit_parameters(source("Berta_CNC"), parsed)
        self.assertEqual(["Berta M110/M111 effects and G54 offsets are unmodeled: commanded-path comparison only"], issues)
        self.assertEqual(.25, next(e["seconds"] for e in parsed["events"] if e["kind"] == "dwell"))
        self.assertTrue(parsed["explicitTermination"])

    def test_berta_blending_tolerance_is_in_source_units_and_not_inferred_machine_accuracy(self):
        for units,expected in (("G21",.03), ("G20",.762)):
            parsed = read_program(BERTA.replace("G21",units), "Berta_CNC")
            blend = next(e for e in parsed["bertaControls"]["setup"] if e["kind"] == "blending")
            self.assertAlmostEqual(expected, blend["toleranceMm"])
        self.assertEqual(-1, read_program(BERTA,"Berta_CNC")["cuts"][0]["to"][2])  # G91.1 is NOT G91.

    def test_missing_or_changed_berta_controls_do_not_pass_the_contract(self):
        for old,new in (("M110\n", ""), ("M111\n", ""), ("M30\n", ""),
                        ("G54\n", ""), ("G91.1\n", ""), ("P0.03", "P0.04")):
            parsed = read_program(BERTA.replace(old,new), "Berta_CNC")
            self.assertGreater(len(audit_parameters(source("Berta_CNC"), parsed)),1, (old,new))
        for old,new in (("M110", "M112"), ("G54", "G55"), ("G91.1", "G91"), ("M110", "M110 X1")):
            with self.assertRaises(ValueError): read_program(BERTA.replace(old,new), "Berta_CNC")
        with self.assertRaises(ValueError): read_program(BERTA, "GRBL_11_no_M6")

    def test_berta_completion_before_parking_and_late_initialization_are_not_the_same_contract(self):
        for changed in (BERTA.replace("G0 Z5\nM111", "M111\nG0 Z5"),
                        BERTA.replace("M110\n", "").replace("G0 Z15\n", "G0 Z15\nM110\n", 1),
                        BERTA.replace("G54\n", "").replace("G0 Z15\n", "G0 Z15\nG54\n", 1)):
            parsed = read_program(changed, "Berta_CNC")
            self.assertGreater(len(audit_parameters(source("Berta_CNC"), parsed)), 1)


if __name__ == "__main__": unittest.main()
