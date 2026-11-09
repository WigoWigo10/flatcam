import unittest
from shapely.geometry import LineString,GeometryCollection,box,Point
from audit_lines_paths import audit,overlap_example


class RawLinesAuditTest(unittest.TestCase):
    def test_reversed_duplicate_is_seen_before_spatial_union(self):
        a=LineString([(1,1),(9,1)]);raw=GeometryCollection([a,LineString(list(a.coords)[::-1])])
        result=audit(raw,box(0,0,10,5),Point(20,20).buffer(1),1)
        self.assertAlmostEqual(8,result['repeatedCenterlineLength'])
        self.assertAlmostEqual(50,result['repeatedCenterlinePercent'])
        self.assertAlmostEqual(8,overlap_example(raw)['length'])

    def test_parallel_overlapping_cutter_footprints_are_not_duplicate_centerlines(self):
        a=LineString([(.5,1),(9.5,1)]);b=LineString([(.5,1.6),(9.5,1.6)])
        self.assertGreater(a.buffer(.5).intersection(b.buffer(.5)).area,0)
        result=audit(GeometryCollection([a,b]),box(0,0,10,5),Point(20,20).buffer(1),1)
        self.assertAlmostEqual(0,result['repeatedCenterlineLength'])
        self.assertEqual(0,result['interiorEndpointCount'])

    def test_ends_at_an_obstacle_are_expected_but_a_truncated_interior_end_is_reported(self):
        area=box(0,0,10,6).difference(Point(5,3).buffer(1,resolution=64));copper=Point(5,3).buffer(1,resolution=64)
        self.assertGreater(area.area,50)
        safe=area.buffer(-.5,resolution=64)
        clipped=LineString([(0,3),(10,3)]).intersection(safe)
        self.assertEqual(0,audit(clipped,area,copper,1)['interiorEndpointCount'])
        truncated=LineString([(.5,1),(7,1)])
        self.assertEqual(1,audit(truncated,area,copper,1)['interiorEndpointCount'])


if __name__=='__main__':unittest.main()
