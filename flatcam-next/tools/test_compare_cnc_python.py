"""Pure reader/audit regressions: no Qt, Python application or private fixture."""
import copy
import unittest
from compare_cnc_python import read_program, equivalent, machining_signature, first_difference, audit_parameters


PROGRAM = """G21
G90
G17
G94
M5
G0 Z15
T1
M0
G90
G0 Z3
M3 S10000
G4 P0.25
G0 X10 Y0
G1 Z-0.4 F100
G0 Z3
G1 Z-0.8 F100
G0 Z3
G1 Z-1.1 F100
G0 Z3
M5
G0 Z5
M30
"""


def case():
    return {"operation":"drilling","units":"MM","tools":[{"id":1,"points":[[10,0]],
        "parameters":{"safeZ":3,"depth":1.1,"multiDepth":True,"depthPerPass":.4,"feedZ":100,
                      "rpm":10000,"offsetZ":0,"dwell":True,"dwellSeconds":.25}}],
        "job":{"toolChange":True,"toolChangeZ":15,"endZ":5,"endX":None,"endY":None,"startZ":None}}


class CncReaderTest(unittest.TestCase):
    def test_modal_feed_and_depths_match_requested_parameters(self):
        parsed = read_program(PROGRAM)
        self.assertEqual(3,len(parsed["cuts"]))
        self.assertEqual([-.4,-.8,-1.1],[event["to"][2] for event in parsed["cuts"]])
        self.assertEqual([],audit_parameters(case(),parsed))
        self.assertTrue(parsed["explicitTermination"])

    def test_feed_can_be_on_its_own_block(self):
        program = PROGRAM.replace("G1 Z-0.4 F100","G1 F100\nG1 Z-0.4")
        self.assertTrue(equivalent(read_program(PROGRAM)["events"],read_program(program)["events"]))

    def test_comments_cannot_inject_commands(self):
        self.assertEqual(read_program(PROGRAM),read_program("(G91 X100 M3 S999)\n; G20\n"+PROGRAM))

    def test_missing_spindle_stop_before_initial_pause_is_detected(self):
        parsed = read_program(PROGRAM.replace("M5\n","",1))
        self.assertIn("Tool-change pause without an explicit preceding spindle stop",audit_parameters(case(),parsed))

    def test_unknown_commands_incremental_units_switch_and_duplicate_words_fail_closed(self):
        for block in ("G91","G2 X10 Y0 I2","G93","M99","X1 X2","G20","G1 ZNaN","G4", "G1 X1 B2"):
            with self.subTest(block=block), self.assertRaises(ValueError):
                read_program(PROGRAM.replace("M30",block+"\nM30"))

    def test_a_dwell_is_not_a_coordinate_move(self):
        with self.assertRaises(ValueError):
            read_program(PROGRAM.replace("G4 P0.25","G4 X2 P0.25"))

    def test_missing_cuts_and_commands_after_termination_fail(self):
        for program in ("G21\nG90\nG0 Z3",PROGRAM+"G0 X0",PROGRAM.replace("G90\n",""),PROGRAM.replace("G94\n","")):
            with self.assertRaises(ValueError):
                read_program(program)

    def test_wrong_depth_feed_tool_spindle_and_dwell_are_not_hidden_by_xy_agreement(self):
        for old,new in (("Z-1.1","Z-1.2"),("F100","F90"),("T1","T2"),("S10000","S9000"),("P0.25","P0.5")):
            with self.subTest(new=new):
                changed = read_program(PROGRAM.replace(old,new))
                self.assertFalse(equivalent(machining_signature(read_program(PROGRAM)),machining_signature(changed)))
                self.assertTrue(audit_parameters(case(),changed))

    def test_repeated_holes_and_passes_are_not_removed(self):
        changed = read_program(PROGRAM.replace("M5\nG0 Z5","G1 Z-1.1 F100\nG0 Z3\nM5\nG0 Z5"))
        self.assertEqual(4,len(changed["cuts"]))
        self.assertTrue(audit_parameters(case(),changed))

    def test_equal_wrong_outputs_still_fail_parameter_audit(self):
        changed = read_program(PROGRAM.replace("F100","F75"))
        self.assertTrue(equivalent(changed,changed))
        self.assertTrue(audit_parameters(case(),changed))

    def test_extra_safe_travel_is_not_full_execution_equivalence(self):
        baseline = read_program(PROGRAM)
        changed = read_program(PROGRAM.replace("G0 Z15","G0 Z3\nG0 Z15",1))
        self.assertTrue(equivalent(machining_signature(baseline),machining_signature(changed)))
        self.assertIsNotNone(first_difference(baseline["events"],changed["events"]))

    def test_rounding_bound_does_not_hide_a_material_depth_change(self):
        self.assertTrue(equivalent(.043307,.0433))
        self.assertFalse(equivalent(.0433,.0443))
        self.assertFalse(equivalent([1],[1,1]))
        self.assertFalse(equivalent(True,1))

    def test_tool_change_and_travel_heights_are_audited(self):
        changed = read_program(PROGRAM.replace("G0 Z15","G0 Z12",1))
        self.assertIn("Tool-change pause Z differs from the requested height",audit_parameters(case(),changed))
        changed = read_program(PROGRAM.replace("G0 Z3\nM3","G0 Z2\nM3",1))
        self.assertIn("Rapid XY below the selected tool's requested Travel Z",audit_parameters(case(),changed))

    def test_audit_does_not_mutate_input(self):
        source = case()
        before = copy.deepcopy(source)
        audit_parameters(source,read_program(PROGRAM))
        self.assertEqual(before,source)

    def test_exclusion_audit_checks_executed_z_not_only_the_xy_preview(self):
        source = case()
        source["tools"][0]["diameter"] = 1
        source["job"]["exclusions"] = [{"wkt":"POLYGON ((4 -1,6 -1,6 1,4 1,4 -1))","strategy":"over","overZ":8}]
        parsed = read_program(PROGRAM)
        self.assertIn("Over travel crosses an exclusion below its requested height",audit_parameters(source,parsed))
        safe = read_program(PROGRAM.replace("G0 X10 Y0","G0 Z8\nG0 X10 Y0\nG0 Z3"))
        self.assertEqual([],audit_parameters(source,safe))
        source["job"]["exclusions"][0]["strategy"] = "around"
        self.assertIn("Around travel intersects an exclusion's tool-radius footprint",audit_parameters(source,safe))


if __name__ == "__main__":
    unittest.main()
