import unittest
from icp_execution import read_icp, UnsupportedIcp


PROGRAM = """; original syntax
IMF_PBL test
FASTVEL 15000
SPINDLE OFF
FASTABS Z15000
GETTOOL 2
FASTABS X3000 Y7000
SPINDLE CCW RPM9000
VEL 2000
MOVEABS Z-500
FASTVEL 25000
MOVEABS X6000
WAIT 250
SPINDLE OFF
FASTABS Z3000
PROGEND
"""


class IcpExecutionTest(unittest.TestCase):
    def test_integer_units_separate_velocities_macro_and_wait(self):
        parsed = read_icp(PROGRAM)
        self.assertEqual([6, 7, 3], parsed["end"])
        self.assertEqual(120, parsed["cuts"][-1]["feed"])
        self.assertEqual(1500, parsed["events"][-2]["feed"])
        self.assertEqual([0, 0, 15], parsed["icpControls"]["toolChanges"][0]["at"])
        self.assertTrue(parsed["icpControls"]["toolChanges"][0]["spindleStopped"])
        self.assertEqual(4, parsed["cuts"][-1]["spindle"])
        self.assertEqual(.25, next(e["seconds"] for e in parsed["events"] if e["kind"] == "dwell"))

    def test_origin_change_is_not_normalized_or_counted_as_execution(self):
        with self.assertRaises(UnsupportedIcp) as caught:
            read_icp(PROGRAM.replace("PROGEND", "WPCLEAR\nFASTABS Z0\nFASTABS X0 Y0\nPROGEND"))
        prefix = caught.exception.prefix
        self.assertFalse(prefix["explicitTermination"])
        self.assertEqual(2, len(prefix["cuts"]))
        self.assertIn("WPCLEAR", str(caught.exception))

    def test_malformed_unknown_truncated_and_out_of_range_fail_closed(self):
        for replacement in ("VEL 0", "VEL 1.5", "VEL 2147483648", "MOVEABS X1 X2", "MOVEABS Q1",
                            "FASTABS X0.5", "GETTOOL 0", "WAIT -1", "INCH", "REF XYZ"):
            with self.subTest(replacement=replacement), self.assertRaises(ValueError):
                read_icp(PROGRAM.replace("VEL 2000", replacement))
        with self.assertRaises(ValueError): read_icp(PROGRAM.replace("PROGEND", ""))
        with self.assertRaises(ValueError): read_icp(PROGRAM + "FASTABS X1\n")
        with self.assertRaises(ValueError): read_icp(PROGRAM.replace("VEL 2000\n", ""))

    def test_undeclared_rapid_speed_remains_unknown_and_quantization_is_visible(self):
        parsed = read_icp(PROGRAM.replace("FASTVEL 15000\n", "").replace("FASTVEL 25000\n", "")
                          .replace("VEL 2000", "VEL 1666"))
        self.assertTrue(all(e["feed"] is None for e in parsed["events"] if e["kind"] == "rapid"))
        self.assertEqual(99.96, parsed["cuts"][0]["feed"])
        self.assertNotEqual(100, parsed["cuts"][0]["feed"])

    def test_spindle_direction_without_an_rpm_word_does_not_invent_zero_rpm(self):
        parsed = read_icp(PROGRAM.replace("SPINDLE CCW RPM9000", "SPINDLE CCW"))
        self.assertIsNone(parsed["cuts"][0]["rpm"])
        self.assertEqual(4, parsed["cuts"][0]["spindle"])


if __name__ == "__main__": unittest.main()
