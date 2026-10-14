"""Pure reader/audit regressions: no Qt, Python application or private fixture."""
import copy
import unittest
from compare_cnc_python import read_program, equivalent, machining_signature, first_difference, audit_parameters, rapid_metrics
from compare_cnc_python import geometry_source,geometry_paths,nearest_points,slot_oracle_case


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
    def test_ring_type_is_explicit_and_cannot_be_forged_by_an_open_linestring(self):
        tool={"wkt":"LINESTRING (10 0,12 0,12 2,10 2,10 0)","geometryType":"LinearRing"}
        self.assertEqual("LinearRing",geometry_source(tool).geom_type)
        tool["wkt"]="LINESTRING (10 0,12 0)"
        with self.assertRaises(ValueError): geometry_source(tool)
        tool["geometryType"]="Polygon"
        with self.assertRaises(ValueError): geometry_source(tool)

    def test_polygon_holes_and_wrong_edges_are_independently_audited(self):
        from shapely import wkt
        shape=wkt.loads("POLYGON ((10 0,12 0,12 2,10 2,10 0),(10.5 .5,11.5 .5,11.5 1.5,10.5 1.5,10.5 .5))")
        paths=geometry_paths(shape); self.assertEqual(2,len(paths))
        source=case(); source["operation"]="geometry"
        source["tools"][0]["wkt"]=shape.wkt
        source["tools"][0]["parameters"].update(depth=.4,multiDepth=False,feed=100)
        def program(contours):
            text="G21\nG90\nG94\nM5\nG0 Z15\nT1\nM0\nG0 Z3\nM3 S10000\nG4 P0.25\n"
            for contour in contours:
                points=list(contour.coords)
                text+=f"G0 X{points[0][0]} Y{points[0][1]}\nG1 Z-0.4 F100\n"
                for x,y in points[1:]: text+=f"G1 X{x} Y{y} F100\n"
                text+="G0 Z3\n"
            return text+"M5\nG0 Z5\nM30\n"
        valid=program(paths)
        self.assertEqual([],audit_parameters(source,read_program(valid)))
        omitted=audit_parameters(source,read_program(program(paths[:1])))
        self.assertIn("T1: missing source cut segments/depths",omitted)
        changed=audit_parameters(source,read_program(valid.replace("G1 X12.0 Y0.0","G1 X13.0 Y0.0")))
        self.assertIn("T1: XY cut segment/depth differs from the source paths",changed)

    def test_duplicate_drill_visits_are_preserved_even_when_python_reorders(self):
        source=case(); source["tools"][0]["points"]=[[10,0],[10,0]]
        source["tools"][0]["parameters"].update(depth=.4,multiDepth=False)
        header=PROGRAM.split("G1 Z-0.4 F100")[0]
        cut="G1 Z-0.4 F100\nG0 Z3\n"
        suffix="M5\nG0 Z5\nM30\n"
        self.assertEqual([],audit_parameters(source,read_program(header+cut+cut+suffix),implementation="python"))
        omitted=audit_parameters(source,read_program(header+cut+suffix),implementation="python")
        self.assertIn("T1: drilled points/count differ from the selected holes",omitted)
        self.assertEqual([[10,0],[10,0],[12,0],[14,0]],nearest_points([[14,0],[10,0],[12,0],[10,0]],[0,0]))

    def test_point_final_depth_and_fx_peck_contract_are_distinguished(self):
        source=case(); source["operation"]="geometry"
        source["tools"][0]["wkt"]="POINT (10 0)"
        source["tools"][0]["parameters"].update(feed=100,fxPassStrategy="CONTINUOUS_SAME_DIRECTION")
        header=PROGRAM.split("G1 Z-0.4 F100")[0]
        final=read_program(header+"G1 Z-1.1 F100\nG0 Z3\nM5\nG0 Z5\nM30\n")
        self.assertEqual([],audit_parameters(source,final,implementation="fx"))
        self.assertEqual([],audit_parameters(source,final,implementation="python"))
        source["tools"][0]["parameters"]["fxPassStrategy"]="RETRACT_SAME_DIRECTION"
        self.assertIn("T1: depths/count differ from the requested passes",audit_parameters(source,final,implementation="fx"))

    def test_explicit_per_tool_slot_settings_do_not_convert_milling_tools_in_fx_audit(self):
        from pathlib import Path
        source=case()
        tool=source["tools"][0]
        tool.update(points=[],slots=[[[10,0],[12,0]]],diameter=1)
        tool["parameters"].update(fxSlotDrilling=True,slotOverlap=25,slotLastHole=False)
        other=copy.deepcopy(tool); other["id"]=2; other["parameters"]["fxSlotDrilling"]=False
        source["tools"].append(other)
        converted=slot_oracle_case(source,Path(__file__).resolve().parents[2],enabled_only=True)
        self.assertEqual([[10,0],[10.75,0],[11.5,0]],converted["tools"][0]["points"])
        self.assertEqual([],converted["tools"][0]["slots"])
        self.assertEqual([],converted["tools"][1]["points"])
        self.assertEqual(other["slots"],converted["tools"][1]["slots"])
        self.assertNotEqual(source,converted)
        self.assertEqual(1,len(source["tools"][0]["slots"]))

    def test_original_diagonal_slot_can_stop_early_on_exact_within_predicate(self):
        from pathlib import Path
        source=case(); tool=source["tools"][0]
        source["job"].update(pythonSlotOverlap=0,pythonSlotLastHole=True)
        tool.update(points=[],slots=[[[3,4],[0,0]]],diameter=1)
        converted=slot_oracle_case(source,Path(__file__).resolve().parents[2])
        # Locked Shapely 1.8.5.post1 / GEOS 3.10.3 oracle: do not hide its numerical
        # early-stop defect or change the legacy body to make it match FX's six centres.
        self.assertEqual([[3,4],[0,0]],converted["tools"][0]["points"])

    def test_original_slot_conversion_is_a_drill_chain_and_does_not_change_the_case(self):
        from pathlib import Path
        source=case(); tool=source["tools"][0]
        tool.update(diameter=1,points=[],slots=[[[10,0],[12,0]]])
        source["job"].update(pythonSlotOverlap=25,pythonSlotLastHole=True)
        before=copy.deepcopy(source)
        converted=slot_oracle_case(source,Path(__file__).resolve().parents[2])
        self.assertEqual([[10,0],[10.75,0],[11.5,0],[12,0]],converted["tools"][0]["points"])
        self.assertEqual([],converted["tools"][0]["slots"]); self.assertEqual(before,source)
        source["job"]["pythonSlotOverlap"]=100
        with self.assertRaises(ValueError): slot_oracle_case(source,Path(__file__).resolve().parents[2])

    def test_rapid_metrics_use_emitted_movements_not_cut_distance_or_time(self):
        parsed={"events":[{"kind":"rapid","from":[0,0,3],"to":[3,4,3]},
                          {"kind":"rapid","from":[3,4,3],"to":[3,4,7]},
                          {"kind":"linear","from":[3,4,7],"to":[100,4,-1]}]}
        self.assertEqual({"xyDistance":5,"zDistance":4,"xyMoves":1},rapid_metrics(parsed))

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

    def test_an_extra_xy_pass_at_the_same_final_z_is_detected(self):
        source = case()
        source["operation"] = "geometry"
        source["tools"][0]["wkt"] = "LINESTRING (10 0,12 0)"
        source["tools"][0]["parameters"]["feed"] = 100
        program = PROGRAM.replace("G1 Z-0.4 F100", "G1 Z-0.4 F100\nG1 X12 Y0 F100")
        program = program.replace("G1 Z-0.8 F100", "G0 X10 Y0\nG1 Z-0.8 F100\nG1 X12 Y0 F100")
        program = program.replace("G1 Z-1.1 F100", "G0 X10 Y0\nG1 Z-1.1 F100\nG1 X12 Y0 F100")
        self.assertEqual([], audit_parameters(source, read_program(program)))
        extra = program.replace("G1 X12 Y0 F100\nG0 Z3\nM5", "G1 X12 Y0 F100\nG1 X10 Y0 F100\nG0 Z3\nM5")
        self.assertIn("T1: XY cutting traversal count differs from the requested passes",
                      audit_parameters(source, read_program(extra)))

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

    def test_fx_job_clearance_is_audited_separately_from_the_legacy_policy(self):
        source = case()
        source["operation"] = "geometry"
        source["tools"][0]["wkt"] = "LINESTRING (10 0,12 0)"
        source["tools"][0]["parameters"]["feed"] = 100
        # A second selected tool has a higher Travel Z. At T1, Z3 satisfies
        # its individual setting but violates the FX job-wide Z4 contract.
        second = copy.deepcopy(source["tools"][0])
        second["id"] = 2
        second["parameters"]["safeZ"] = 4
        source["tools"].append(second)
        parsed = read_program(PROGRAM)
        self.assertNotIn("Rapid XY below FX job-wide maximum Travel Z",audit_parameters(source,parsed))
        self.assertIn("Rapid XY below FX job-wide maximum Travel Z",audit_parameters(source,parsed,implementation="fx"))
        source["job"]["fxTravelClearance"]="PER_TOOL"
        self.assertNotIn("Rapid XY below FX job-wide maximum Travel Z",audit_parameters(source,parsed,implementation="fx"))
        source["job"]["fxTravelClearance"]="JOB_MAXIMUM"
        raised = read_program(PROGRAM.replace("G0 Z3\nM3","G0 Z4\nM3",1))
        self.assertNotIn("Rapid XY below FX job-wide maximum Travel Z",audit_parameters(source,raised,implementation="fx"))

    def test_zero_rpm_fx_contract_and_unknown_travel_policy_are_not_silently_accepted(self):
        source=case()
        source["tools"][0]["parameters"]["rpm"]=0
        # No automatic start is allowed even if the controller sees no S word.
        unexpected=read_program(PROGRAM.replace("M3 S10000","M3"))
        self.assertIn("T1: RPM zero must not command a spindle start in FX",audit_parameters(source,unexpected,implementation="fx"))
        manual=read_program(PROGRAM.replace("M3 S10000\n",""))
        self.assertNotIn("T1: RPM zero must not command a spindle start in FX",audit_parameters(source,manual,implementation="fx"))
        source["job"]["fxTravelClearance"]="unknown"
        with self.assertRaises(ValueError):
            audit_parameters(source,manual,implementation="fx")

    def test_separate_linear_paths_require_all_plunges_and_traversals(self):
        source = case()
        source["operation"] = "geometry"
        tool = source["tools"][0]
        tool["wkt"] = "MULTILINESTRING ((10 0,12 0),(14 0,16 0))"
        tool["parameters"].update(depth=.4,multiDepth=False,feed=100)
        first = "G1 Z-0.4 F100\nG1 X12 Y0 F100\nG0 Z3\n"
        second = "G0 X14 Y0\nG1 Z-0.4 F100\nG1 X16 Y0 F100\nG0 Z3\n"
        header = PROGRAM.split("G1 Z-0.4 F100")[0]
        suffix = "M5\nG0 Z5\nM30\n"
        self.assertEqual([],audit_parameters(source,read_program(header+first+second+suffix)))
        incomplete = audit_parameters(source,read_program(header+first+suffix))
        self.assertIn("T1: depths/count differ from the requested passes",incomplete)
        self.assertIn("T1: XY cutting traversal count differs from the requested passes",incomplete)

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
