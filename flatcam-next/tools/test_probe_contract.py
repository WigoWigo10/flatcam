import unittest
from compare_cnc_python import read_program,audit_parameters

PROGRAM="G21\nG90\nG94\nM5\nG0 Z15\nT1\nM6\nM5\nG90\nG21\nG0 Z15\nM0\nG31 Z-5 F50\nM0\nG92 Z0\nG0 Z1.5\nG31 Z-5 F25\nM0\nG92 Z0\nG0 Z3\nM0\nM3 S10000\nG0 X10 Y7\nG1 Z-.6 F75\nG1 X12 Y7 F200\nM5\nG0 Z3\n"


def source():
    return dict(profile="Toolchange_Probe_MACH3",units="MM",tools=[dict(id=1)],
                job=dict(probe=dict(depth=-5,feed=50,contactZ=0)))


class ProbeContractTest(unittest.TestCase):
    def test_contact_is_never_fabricated_and_static_contract_is_not_execution(self):
        parsed=read_program(PROGRAM,"Toolchange_Probe_MACH3")
        self.assertEqual([],audit_parameters(source(),parsed))
        self.assertEqual([],parsed["cuts"])
        self.assertEqual([None,None,None],parsed["end"])
        self.assertFalse(parsed["probeControls"]["executionComparable"])

    def test_missing_confirmation_wrong_feed_and_contact_height_do_not_pass(self):
        for old,new in (("G31 Z-5 F50\nM0\n","G31 Z-5 F50\n"),("F25","F50"),("G92 Z0","G92 Z1"),
                        ("M5\nG90\nG21","G90\nG21"),("M6\nM5\nG90","M6\nM5"),
                        ("G0 Z3\nM0\nM3","G0 Z3\nM3")):
            parsed=read_program(PROGRAM.replace(old,new),"Toolchange_Probe_MACH3")
            self.assertTrue(audit_parameters(source(),parsed),(old,new))

    def test_unknown_contact_macros_arcs_and_relative_motion_fail_closed(self):
        for bad in ("G31 Z#2002 F50","G10 L2 P1 Z0","G91","G2 X1 Y2 I3 J4","M98 P100","G31 Z-5 Z-4"):
            with self.subTest(bad=bad),self.assertRaises(ValueError):
                read_program(PROGRAM+bad,"Toolchange_Probe_MACH3")

    def test_feed_before_an_opaque_macro_does_not_establish_the_probe_feed(self):
        code=PROGRAM.replace("M6","G1 F50\nM6").replace("G31 Z-5 F50","G31 Z-5")
        parsed=read_program(code,"Toolchange_Probe_MACH3")
        self.assertIn("T1: wrong fast/slow probe feed",audit_parameters(source(),parsed))
