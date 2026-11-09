import unittest
import math
from shapely.geometry import LineString,GeometryCollection,box,Point
from audit_lines_paths import audit,overlap_example


class RawLinesAuditTest(unittest.TestCase):
    def test_a_boundary_pass_one_ulp_outside_geos_is_reported_without_hiding_the_exact_miss(self):
        area=box(0,0,1,1);diameter=.2
        safe=area.buffer(-diameter/1.99999999,resolution=64);x,y,right,top=safe.bounds
        raw=LineString([(math.nextafter(x,-math.inf),y),(math.nextafter(x,-math.inf),top)])
        result=audit(raw,area,Point(20,20).buffer(1),diameter,False)['regions']
        self.assertEqual(0,result['unservedReachableComponentCount'])
        self.assertEqual(1,result['exactUnservedReachableComponentCount'])
        self.assertEqual(1,result['nearBoundaryComponentCount'])

    def test_numerical_tolerance_does_not_turn_an_endpoint_touch_into_a_pass(self):
        area=box(0,0,1,1);diameter=.2
        x,y,right,top=area.buffer(-diameter/1.99999999,resolution=64).bounds
        raw=LineString([(x-1,y),(x,y)])
        result=audit(raw,area,Point(20,20).buffer(1),diameter,False)['regions']
        self.assertEqual(1,result['exactUnservedReachableComponentCount'])
        self.assertEqual(0,result['nearBoundaryComponentCount'])
        self.assertEqual(1,result['unservedReachableComponentCount'])
    def test_a_path_in_a_large_component_does_not_hide_an_unserved_small_island(self):
        area=box(0,0,10,6).union(Point(12,1.05).buffer(.21,resolution=64))
        raw=LineString([(.21,1),(9.79,1)])
        before=audit(raw,area,Point(20,20).buffer(1),.4,False)
        self.assertEqual(1,before['regions']['unservedReachableComponentCount'])
        fixed=GeometryCollection([raw,LineString([(11.995,1.05),(12.005,1.05)])])
        after=audit(fixed,area,Point(20,20).buffer(1),.4,False)
        self.assertEqual(0,after['regions']['unservedReachableComponentCount'])
        self.assertEqual('NOT_MEASURED',after['coverageStatus'])
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
