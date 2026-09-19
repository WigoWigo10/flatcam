# -*- coding: utf-8 -*-
"""
Fase 0 baseline test (see CONTEXTO_FLATCAM_FX.md, secoes 8 e 9): locks down the
CURRENT behavior of appParsers.ParseExcellon.Excellon against a small fixture
corpus (units/zeros detection, per-tool diameters, drill/slot counts, bounds),
so a future refactor or Java port has a real oracle to diff against instead of
"it still looks right".

Scoped to parsing only (parse_file), not create_geometry()/CNCJob generation -
see the docstring in tests/parser_baseline.py.
"""
import unittest

from tests.parser_baseline import (
    EXCELLON_FIXTURES,
    parse_excellon,
    summarize_excellon,
    load_baseline,
)

FLOAT_PLACES = 6


class ExcellonCharacterizationTestCase(unittest.TestCase):
    def _assert_matches_baseline(self, fixture):
        excellon = parse_excellon(fixture)
        actual = summarize_excellon(excellon)
        expected = load_baseline('excellon', fixture)

        self.assertEqual(actual['units'], expected['units'], fixture)
        self.assertEqual(actual['zeros'], expected['zeros'], fixture)
        self.assertEqual(actual['tool_count'], expected['tool_count'], fixture)
        self.assertEqual(actual['total_drills'], expected['total_drills'], fixture)
        self.assertEqual(actual['total_slots'], expected['total_slots'], fixture)
        self.assertEqual(set(actual['tools'].keys()), set(expected['tools'].keys()), fixture)
        for tool_id, expected_tool in expected['tools'].items():
            actual_tool = actual['tools'][tool_id]
            self.assertEqual(actual_tool['drill_count'], expected_tool['drill_count'], (fixture, tool_id))
            self.assertEqual(actual_tool['slot_count'], expected_tool['slot_count'], (fixture, tool_id))
            if expected_tool['tooldia'] is None:
                self.assertIsNone(actual_tool['tooldia'], (fixture, tool_id))
            else:
                self.assertAlmostEqual(
                    actual_tool['tooldia'], expected_tool['tooldia'],
                    places=FLOAT_PLACES, msg=(fixture, tool_id),
                )

        self.assertEqual(actual['bounds'] is None, expected['bounds'] is None, fixture)
        if actual['bounds'] is not None:
            for got, want in zip(actual['bounds'], expected['bounds']):
                self.assertAlmostEqual(got, want, places=FLOAT_PLACES, msg=fixture)


def _make_test(fixture):
    def test(self):
        self._assert_matches_baseline(fixture)

    return test


for _fixture in EXCELLON_FIXTURES:
    _name = 'test_' + _fixture.rsplit('/', 1)[-1].replace('.', '_').replace('-', '_')
    setattr(ExcellonCharacterizationTestCase, _name, _make_test(_fixture))


if __name__ == '__main__':
    unittest.main()
