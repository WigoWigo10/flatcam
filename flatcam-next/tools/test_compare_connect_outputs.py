import json
from pathlib import Path
import tempfile
import unittest
from compare_connect_outputs import compare


class CompareConnectOutputsTest(unittest.TestCase):
    def fixture(self,first,second):
        case={'id':'fixture','board':'fixture','units':'MM','operation':'ncc','diameter':.4,
              'overlap':.4,'connect':True,'contour':False,'boundaryPolicy':'explicit diagnostic rectangle',
              'rawPathsWkt':'LINESTRING (0.2 0.2, 0.8 0.2)',
              'clearingAreaWkt':'POLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))',
              'copperWkt':'POLYGON ((20 20, 21 20, 21 21, 20 21, 20 20))',
              'failedPolygons':0,'regionDiagnostics':[],'status':'GENERATED','elapsedMs':100}
        for folder in (first,second):
            folder.mkdir();(folder/'fixture.json').write_text(json.dumps(case),encoding='utf-8')
        return case

    def test_timing_changes_do_not_change_the_behavior_result(self):
        with tempfile.TemporaryDirectory() as temporary:
            first=Path(temporary)/'before';second=Path(temporary)/'after';case=self.fixture(first,second)
            case['elapsedMs']=1;(second/'fixture.json').write_text(json.dumps(case),encoding='utf-8')
            self.assertEqual(1,compare(first,second)['counts']['EXACT_MATCH'])

    def test_reversing_the_same_geometric_line_is_a_behavior_change(self):
        with tempfile.TemporaryDirectory() as temporary:
            first=Path(temporary)/'before';second=Path(temporary)/'after';case=self.fixture(first,second)
            case['rawPathsWkt']='LINESTRING (0.8 0.2, 0.2 0.2)'
            (second/'fixture.json').write_text(json.dumps(case),encoding='utf-8')
            report=compare(first,second)
            self.assertEqual(1,report['counts']['DIFFERENT'])
            self.assertEqual(['rawPathsWkt'],report['cases'][0]['differingFields'])

    def test_a_budget_failure_or_missing_case_cannot_count_as_a_match(self):
        with tempfile.TemporaryDirectory() as temporary:
            first=Path(temporary)/'before';second=Path(temporary)/'after';case=self.fixture(first,second)
            case['status']='ERROR_OR_BUDGET';(second/'fixture.json').write_text(json.dumps(case),encoding='utf-8')
            self.assertEqual(1,compare(first,second)['counts']['UNVERIFIED'])
            (second/'fixture.json').unlink()
            self.assertEqual(1,compare(first,second)['counts']['UNVERIFIED'])


if __name__=='__main__':unittest.main()
