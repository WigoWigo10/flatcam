"""Public diagnostic helpers never turn operand controls into parity approvals."""
import json
import unittest
from shapely.geometry import MultiPolygon, Point
from compare_ncc_union_probe import audit, public_oracle


class UnionProbeTest(unittest.TestCase):
    def test_public_golden_reports_every_input_part_and_an_original_vertex(self):
        for count in (1,12,47,80):
            parts=[Point((i*17%count)%7*6,(i*17%count)//7*5).buffer(1,4) for i in range(count)]
            document={"cases":[{"count":count,"sourceWkt":MultiPolygon(parts).wkt}]}
            before=json.dumps(document)
            case=public_oracle(document)["cases"][0]
            self.assertEqual(list(range(count)),sorted(case["order"]))
            self.assertTrue(all(0<=vertex<16 for vertex in case["vertices"]))
            self.assertEqual(before,json.dumps(document))

    def test_binary_and_unary_columns_are_explicit_diagnostics(self):
        left=Point(0,0).buffer(1,4)
        right=Point(6,0).buffer(1,4)
        source=MultiPolygon([left,right])
        document={"cases":[{"id":"public","inputWkt":source.wkt,"candidateCopperWkt":source.wkt,
                 "unionOperations":[{"range":"0:2","left":left.wkt,"right":right.wkt,"result":source.wkt}]}]}
        before=json.dumps(document)
        report=audit(document)
        self.assertIn("not production parity",report["scope"])
        operation=report["cases"][0]["operations"][0]
        self.assertIn("pythonBinary",operation)
        self.assertIn("pythonUnary",operation)
        self.assertNotIn("matchesSampledCriteria",operation)
        self.assertEqual(0,operation["areaDelta"])
        self.assertEqual(before,json.dumps(document))


if __name__=="__main__":
    unittest.main()
