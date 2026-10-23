"""Profile contracts: three manual phases are not a generic pause or an M6 macro."""
import unittest
from compare_cnc_python import read_program, audit_parameters, UnmodeledToolSelection


def source(profile, active=True):
    return dict(profile=profile, units="MM", operation="drilling",
        tools=[dict(id=1,diameter=1,points=[[10,0]],parameters=dict(safeZ=3,depth=1,multiDepth=False,
            depthPerPass=1,feedZ=50,rpm=10000,offsetZ=0,dwell=False,dwellSeconds=0))],
        job=dict(toolChange=active,toolChangeZ=15,startZ=None,endZ=5,endX=None,endY=None,
                 changeX=None,changeY=None,rapidFeed=1500))


HEADER="G21\nG90\nG94\nM5\nG0 Z15\nT1\n"
TAIL="G0 Z3\nM3 S10000\nG0 X10 Y0\nG1 Z-1 F50\nM5\nG0 Z5\n"
MANUAL=HEADER+"M0\nG90\nG1 Z0 F50\nM0\nG90\nG0 Z15\nM0\nG90\n"+TAIL
CUSTOM=HEADER+"M6\nG90\nG0 Z15\n"+TAIL


class ToolchangeProfilesTest(unittest.TestCase):
    def test_manual_has_three_different_heights_and_explicit_z_feed(self):
        parsed=read_program(MANUAL,"Toolchange_Manual")
        self.assertEqual([],audit_parameters(source("Toolchange_Manual"),parsed,implementation="fx"))
        cycle=parsed["toolChangeControls"]["cycles"][0]
        self.assertEqual([15,0,15],[e["at"][2] for e in cycle["pauses"]])
        self.assertEqual(50,cycle["adjustment"][0]["feed"])

    def test_wrong_adjustment_feed_is_visible_even_when_cutting_feed_is_correct(self):
        parsed=read_program(MANUAL.replace("G1 Z0 F50","G1 Z0 F300"),"Toolchange_Manual")
        self.assertIn("T1: manual Z0 adjustment feed differs from this tool's Feed Z",
                      audit_parameters(source("Toolchange_Manual"),parsed,implementation="fx"))

    def test_missing_pause_wrong_return_and_m6_are_not_a_manual_cycle(self):
        for changed in (MANUAL.replace("M0\n","",1),MANUAL.replace("G0 Z15\nM0","G0 Z12\nM0"),
                        MANUAL.replace("T1\n","T1\nM6\n")):
            with self.subTest(changed=changed):
                self.assertTrue(audit_parameters(source("Toolchange_Manual"),read_program(changed,"Toolchange_Manual")))

    def test_custom_requires_a_macro_without_inventing_an_extra_pause(self):
        parsed=read_program(CUSTOM,"Toolchange_Custom")
        self.assertEqual([],audit_parameters(source("Toolchange_Custom"),parsed,implementation="fx"))
        self.assertEqual(1,len(parsed["toolChangeControls"]["cycles"]))
        for changed in (CUSTOM.replace("M6\n",""),CUSTOM.replace("M6\n","M6\nM0\n")):
            self.assertTrue(audit_parameters(source("Toolchange_Custom"),read_program(changed,"Toolchange_Custom")))

    def test_opaque_custom_selection_cannot_be_mapped_to_the_requested_tool(self):
        for changed in (CUSTOM.replace("T1\n",""),CUSTOM.replace("T1\nM6\n","")):
            with self.assertRaises(UnmodeledToolSelection): read_program(changed,"Toolchange_Custom")

    def test_disabled_change_emits_neither_manual_pause_nor_custom_macro(self):
        for profile in ("Toolchange_Manual","Toolchange_Custom"):
            self.assertEqual([],audit_parameters(source(profile,False),read_program(HEADER+TAIL,profile)))
            active=MANUAL if profile=="Toolchange_Manual" else CUSTOM
            self.assertTrue(audit_parameters(source(profile,False),read_program(active,profile)))


if __name__ == "__main__": unittest.main()
