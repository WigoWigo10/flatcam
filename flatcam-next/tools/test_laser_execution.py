"""Laser power/path contract: negative-Z milling is not an oracle for laser exposure."""
import copy
import unittest
from compare_cnc_python import read_program, audit_parameters, equivalent, machining_signature
from laser_execution import LASER_PROFILES, read_laser

GRBL = "G21\nG90\nG17\nG94\nM5\nG0 X10 Y7\nM3 S128\nG1 X12 Y7 F200\nM5\nG0 Z3\n"
FAN = "G21\nG90\nM400\nM107\nG0 Z3 F900\nG0 X10 Y7 F900\nM106 S128\nG1 X12 Y7 F200\nM400\nM107\nG0 Z3 F900\nM400\n"


def source(profile):
    return dict(profile=profile,operation="geometry",units="MM",tools=[dict(id=1,diameter=.4,
        wkt="LINESTRING (10 7,12 7)",parameters=dict(safeZ=3,feed=200,rpm=128))],
        job=dict(endZ=3,rapidFeed=900,laserFocusZ=None if profile == "GRBL_laser" else 3))


class LaserExecutionTest(unittest.TestCase):
    def test_powered_xy_is_a_cut_at_nonnegative_z_and_is_not_rpm_or_a_hardware_tool(self):
        parsed = read_program(GRBL,"GRBL_laser")
        self.assertEqual([],audit_parameters(source("GRBL_laser"),parsed))
        self.assertEqual([12,7,0],parsed["cuts"][0]["to"])
        self.assertEqual(128,parsed["cuts"][0]["laserPower"])
        self.assertIsNone(parsed["cuts"][0]["rpm"])
        self.assertIn("NOT a selected hardware tool",parsed["laserControls"]["sourceGroup"])

    def test_unknown_initial_power_is_not_forged_into_off(self):
        parsed = read_laser(GRBL.replace("M5\n","",1),"GRBL_laser")
        rapid = next(e for e in parsed["events"] if e["kind"] == "rapid")
        self.assertIsNone(rapid["laserPower"])
        self.assertTrue(audit_parameters(source("GRBL_laser"),parsed))

    def test_positioning_with_commanded_power_on_is_visible_without_assuming_grbl_mode(self):
        parsed = read_program(GRBL.replace("G0 X10 Y7\nM3 S128", "M3 S128\nG0 X10 Y7"),"GRBL_laser")
        self.assertTrue(audit_parameters(source("GRBL_laser"),parsed))
        self.assertIn("$32",parsed["laserControls"]["limits"])

    def test_marlin_native_modes_and_final_barrier_are_not_normalized_away(self):
        parsed = read_program(FAN,"Marlin_laser_FAN_pin")
        self.assertEqual([],audit_parameters(source("Marlin_laser_FAN_pin"),parsed))
        self.assertTrue(parsed["laserControls"]["finalMovementBarrier"])
        missing = read_program(FAN.removesuffix("M400\n"),"Marlin_laser_FAN_pin")
        self.assertTrue(audit_parameters(source("Marlin_laser_FAN_pin"),missing))
        self.assertTrue(equivalent(machining_signature(parsed),machining_signature(missing)))
        self.assertFalse(equivalent(parsed["events"],missing["events"]))
        with self.assertRaises(ValueError): read_program(FAN.replace("G90","G90\nG94"),"Marlin_laser_FAN_pin")

    def test_marlin_fan_default_is_full_duty_and_invalid_duty_fails_closed(self):
        parsed = read_program(FAN.replace("M106 S128","M106"),"Marlin_laser_FAN_pin")
        self.assertEqual(255,parsed["cuts"][0]["laserPower"])
        self.assertTrue(audit_parameters(source("Marlin_laser_FAN_pin"),parsed))
        for control in ("M106 S256","M106 S-1","M106 S1.5","M106 P1 S128","M3 S128"):
            with self.subTest(control=control), self.assertRaises(ValueError):
                read_program(FAN.replace("M106 S128",control),"Marlin_laser_FAN_pin")
        zero = read_program(FAN.replace("M107","M106 S0"),"Marlin_laser_FAN_pin")
        self.assertEqual(0,zero["laserControls"]["finalPower"])

    def test_marlin_spindle_power_is_raw_s_and_not_pwm_255_or_physical_rpm(self):
        program = FAN.replace("M107","M5").replace("M106 S128","M3 S1000")
        parsed = read_program(program,"Marlin_laser_Spindle_pin")
        self.assertEqual(1000,parsed["cuts"][0]["laserPower"])
        self.assertIsNone(parsed["cuts"][0]["rpm"])
        with self.assertRaises(ValueError): read_program(program.replace("M3 S1000","M3 I S1000"),"Marlin_laser_Spindle_pin")

    def test_focus_z_and_powered_z_moves_are_audited_separately_from_xy_agreement(self):
        wrong = read_program(FAN.replace("G0 Z3 F900","G0 Z2 F900",1),"Marlin_laser_FAN_pin")
        self.assertTrue(audit_parameters(source("Marlin_laser_FAN_pin"),wrong))
        wrong = read_program(FAN.replace("G1 X12 Y7 F200","G1 X12 Y7 Z4 F200"),"Marlin_laser_FAN_pin")
        self.assertIn("Powered Z motion is outside the XY laser contract",audit_parameters(source("Marlin_laser_FAN_pin"),wrong))

    def test_wrong_feed_power_end_z_and_missing_shutdown_do_not_pass_the_audit(self):
        for old,new in (("F200","F150"),("S128","S127"),("G0 Z3","G0 Z4"),("M5\nG0 Z3","G0 Z3")):
            parsed = read_program(GRBL.replace(old,new),"GRBL_laser")
            self.assertTrue(audit_parameters(source("GRBL_laser"),parsed),(old,new))

    def test_repeated_exposure_and_reversed_direction_are_not_merged_for_parity(self):
        base = read_program(GRBL,"GRBL_laser")
        reverse = GRBL.replace("G0 X10 Y7","G0 X12 Y7").replace("G1 X12 Y7 F200","G1 X10 Y7 F200")
        reversed_path = read_program(reverse,"GRBL_laser")
        self.assertEqual([],audit_parameters(source("GRBL_laser"),reversed_path))
        self.assertFalse(equivalent(machining_signature(base),machining_signature(reversed_path)))
        repeated = read_program(GRBL.replace("G1 X12 Y7 F200","G1 X12 Y7 F200\nG1 X10 Y7 F200\nG1 X12 Y7 F200"),"GRBL_laser")
        self.assertEqual(3,len(repeated["cuts"]))
        self.assertIn("Extra/changed powered XY segment or repeated exposure",audit_parameters(source("GRBL_laser"),repeated))

    def test_missing_paths_are_detected_even_if_one_powered_line_remains(self):
        case = source("GRBL_laser")
        case["tools"][0]["wkt"] = "MULTILINESTRING ((10 7,12 7),(20 7,22 7))"
        self.assertIn("Missing powered source XY segments",audit_parameters(case,read_program(GRBL,"GRBL_laser")))

    def test_milling_probe_arcs_dwell_and_tool_changes_cannot_enter_the_laser_reader(self):
        for block in ("T1","M6","M0","G4 P250","G2 X12 Y7 I1 J0","G92 Z0","G91","M30","G0 G1 X1"):
            with self.subTest(block=block), self.assertRaises(ValueError):
                read_program(GRBL.replace("M5\nG0 Z3",block+"\nM5\nG0 Z3"),"GRBL_laser")
        with self.assertRaises(ValueError): read_program(FAN,"GRBL_laser")

    def test_nested_header_comments_are_diagnostic_and_never_controller_parity(self):
        parsed = read_program("(Laser Power (Spindle Speed): 128)\n"+GRBL,"GRBL_laser")
        self.assertEqual(1,sum(e["kind"] == "nonstandardComment" for e in parsed["events"]))
        self.assertIn("Nested-parenthesis header is diagnostic text, not validated controller syntax",audit_parameters(source("GRBL_laser"),parsed))
        for bad in ("(unclosed", ")G0 X1", "G0 X1 (open"):
            with self.assertRaises(ValueError): read_program(bad+"\n"+GRBL,"GRBL_laser")

    def test_marlin_feed_modes_and_inch_units_are_explicit(self):
        program = FAN.replace("G21","G20").replace("G1 X12 Y7 F200","G1 X12 Y7")
        # Supply a known G1 feed before the G0 blocks to distinguish both firmware modes.
        program = program.replace("G90","G90\nG1 F200")
        shared = read_program(program,"Marlin_laser_FAN_pin")
        variable = read_program(program,"Marlin_laser_FAN_pin",marlin_rapid_mode="variable")
        fixed = read_program(program,"Marlin_laser_FAN_pin",marlin_rapid_mode="fixed",marlin_fixed_rapid_feed=1200)
        self.assertEqual("IN",shared["units"])
        self.assertEqual(900,shared["cuts"][0]["feed"])
        self.assertEqual(200,variable["cuts"][0]["feed"])
        self.assertEqual(900,fixed["cuts"][0]["feed"])
        self.assertTrue(all(e["feed"] == 1200 for e in fixed["events"] if e["kind"] == "rapid"))
        with self.assertRaises(ValueError): read_program(FAN,"Marlin_laser_FAN_pin",marlin_rapid_mode="fixed")
        with self.assertRaises(ValueError): read_laser(GRBL,"GRBL_laser",rapid_mode="variable")

    def test_points_and_multiple_source_groups_are_explicitly_out_of_scope(self):
        parsed = read_program(GRBL,"GRBL_laser")
        case = source("GRBL_laser"); case["tools"][0]["wkt"] = "POINT (10 7)"
        with self.assertRaises(ValueError): audit_parameters(case,parsed)
        case = source("GRBL_laser"); case["tools"].append(copy.deepcopy(case["tools"][0]))
        with self.assertRaises(ValueError): audit_parameters(case,parsed)
        case = source("GRBL_laser"); case["tools"][0]["id"] = 7
        with self.assertRaises(ValueError): audit_parameters(case,parsed)


if __name__ == "__main__": unittest.main()
