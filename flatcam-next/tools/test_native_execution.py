import unittest
from compare_cnc_python import read_program, audit_parameters, equivalent, machining_signature

ROLAND=";;^IN;^PA;!MC0;V10;Z0,0,120;!MC1;Z400,280,120;V1;Z400,280,-24;V2;Z480,280,-24;V10;Z480,280,120;!MC0;"


def source():
    return dict(profile="Roland_MDX_20",units="MM",operation="geometry",tools=[dict(id=1,diameter=.8,
        wkt="LINESTRING (10 7,12 7)",parameters=dict(safeZ=3,depth=.6,multiDepth=False,depthPerPass=.2,
            feed=120,feedZ=60,rpm=0))],job=dict(rapidFeed=600,endZ=3,endX=None,endY=None))


class NativeExecutionTest(unittest.TestCase):
    def test_hpgl_units_pen_state_and_two_selected_pens_do_not_invent_z_or_speed(self):
        code='IN;CO "FCFX HPGL UNITS IN";PU;SP1;PA400,280;PD;PA480,280;PU;SP2;PA800,280;PD;PA880,280;PU;SP0;'
        parsed=read_program(code,"hpgl",hpgl_source_units="IN")
        self.assertEqual([1,2],[e["tool"] for e in parsed["cuts"]])
        self.assertAlmostEqual(12/25.4,parsed["cuts"][0]["to"][0])
        self.assertIsNone(parsed["cuts"][0]["feed"])
        self.assertFalse(parsed["hpglControls"]["finalPenDown"])
        with self.assertRaises(ValueError): read_program(code,"hpgl",hpgl_source_units="MM")

    def test_hpgl_unknown_initial_pen_and_missing_pen_up_are_visible(self):
        case=source();case.update(profile="hpgl",tools=[dict(id=1,diameter=.8,wkt="LINESTRING (10 7,12 7)",parameters={})])
        code="IN;PU;SP1;PA400,280;PD;PA480,280;PU;SP0;"
        self.assertEqual([],audit_parameters(case,read_program(code,"hpgl")))
        self.assertTrue(audit_parameters(case,read_program(code.replace("SP1;",""),"hpgl")))
        self.assertTrue(audit_parameters(case,read_program(code.replace("PU;SP0;",""),"hpgl")))

    def test_hpgl_quantization_and_repeated_exposure_do_not_become_false_full_matches(self):
        code="IN;PU;SP1;PA400,280;PD;PA480,280;PU;SP0;"
        parsed=read_program(code,"hpgl")
        case=source();case.update(profile="hpgl",tools=[dict(id=1,diameter=.8,wkt="LINESTRING (10.013 7,12.013 7)",parameters={})])
        self.assertTrue(audit_parameters(case,parsed))
        changed=read_program(code.replace("PA480,280;","PA480,280;PA400,280;PA480,280;"),"hpgl")
        self.assertEqual(3,len(changed["cuts"]))
        self.assertFalse(equivalent(machining_signature(parsed),machining_signature(changed)))

    def test_hpgl_relative_moves_are_explicit_and_unknown_commands_and_overflow_fail(self):
        parsed=read_program("IN;PU;SP1;PA400,280;PD;PR80,0;PU;SP0;","hpgl")
        self.assertEqual([12,7,0],parsed["cuts"][0]["to"])
        for command in ("IN;","VS10;","PA40000,0;","PA1;","SP-1;","SP2;","CO bad;","PA1,NaN;"):
            with self.subTest(command=command),self.assertRaises(ValueError):
                read_program("IN;PU;SP1;PA400,280;PD;PA480,280;"+command,"hpgl")
    def test_roland_has_native_xyz_velocity_and_binary_motor_not_fictitious_rpm(self):
        parsed=read_program(ROLAND,"Roland_MDX_20")
        self.assertEqual([],audit_parameters(source(),parsed))
        self.assertEqual(60,parsed["cuts"][0]["feed"])
        self.assertEqual(120,parsed["cuts"][1]["feed"])
        self.assertIsNone(parsed["cuts"][0]["rpm"])
        self.assertEqual(0,parsed["rolandControls"]["finalMotor"])

    def test_roland_semicolonless_motor_is_supported_but_motor_state_is_not_invented(self):
        parsed=read_program(ROLAND.replace("!MC0;","!MC0\n").replace("!MC1;","!MC1\n"),"Roland_MDX_20")
        self.assertEqual([],audit_parameters(source(),parsed))
        stopped=read_program(ROLAND.replace("!MC1;",""),"Roland_MDX_20")
        self.assertTrue(audit_parameters(source(),stopped))
        no_stop=read_program(ROLAND.removesuffix("!MC0;"),"Roland_MDX_20")
        self.assertTrue(audit_parameters(source(),no_stop))

    def test_roland_wrong_z_feed_rapid_and_directed_path_are_visible(self):
        for old,new in (("-24","-28"),("V1;","V1.5;"),("V10;","V9;"),("Z480,280,-24","Z520,280,-24")):
            changed=read_program(ROLAND.replace(old,new),"Roland_MDX_20")
            self.assertTrue(audit_parameters(source(),changed),(old,new))

    def test_roland_rounding_is_not_hidden_by_relaxed_tolerance(self):
        parsed=read_program(ROLAND,"Roland_MDX_20")
        case=source();case["tools"][0]["parameters"]["feed"]=125
        self.assertTrue(audit_parameters(case,parsed))
        case=source();case["tools"][0]["parameters"]["depth"] = .6006
        self.assertTrue(audit_parameters(case,parsed))

    def test_roland_repeated_cut_is_not_deduplicated(self):
        parsed=read_program(ROLAND.replace("Z480,280,-24;","Z480,280,-24;Z400,280,-24;Z480,280,-24;"),"Roland_MDX_20")
        self.assertEqual(4,len(parsed["cuts"]))
        self.assertFalse(equivalent(machining_signature(parsed),machining_signature(read_program(ROLAND,"Roland_MDX_20"))))
        self.assertTrue(audit_parameters(source(),parsed))

    def test_roland_unknown_reset_relative_commands_and_bad_numbers_fail_closed(self):
        for bad in ("^IN;","^PR;","!MC2;","V0;","V16;","VNaN;","Z1,2;","Z1,2,NaN;","G0 X3;","!ZZ1;"):
            with self.subTest(bad=bad),self.assertRaises(ValueError):
                read_program(ROLAND+bad,"Roland_MDX_20")
        for bad in (";;^IN;^PA;Z1,2,3;","V1;Z1,2,3;",ROLAND.replace("^PA;","")):
            with self.assertRaises(ValueError):read_program(bad,"Roland_MDX_20")
