# -*- coding: utf-8 -*-
"""
Fase 0 baseline test (see CONTEXTO_FLATCAM_FX.md, secoes 8 e 9): locks down the
CURRENT behavior of appParsers.ParseGerber.Gerber against a small fixture
corpus, so that any future change - refactor, dependency bump (e.g. Shapely),
or eventual Java port - can be diffed against a known-good oracle instead of
"it still looks right".

This does NOT judge whether the legacy output is correct Gerber interpretation,
only that it does not silently change. If a change is intentional, regenerate
the golden file with tests/generate_baseline.py and review the diff.
"""
import unittest

from tests.parser_baseline import (
    GERBER_FIXTURES,
    parse_gerber,
    summarize_gerber,
    load_baseline,
)

FLOAT_PLACES = 6


class GerberCharacterizationTestCase(unittest.TestCase):
    def _assert_matches_baseline(self, fixture):
        gerber = parse_gerber(fixture)
        actual = summarize_gerber(gerber)
        expected = load_baseline('gerber', fixture)

        self.assertEqual(actual['units'], expected['units'], fixture)
        self.assertEqual(actual['aperture_count'], expected['aperture_count'], fixture)
        self.assertEqual(actual['aperture_macro_count'], expected['aperture_macro_count'], fixture)
        self.assertEqual(actual['apertures'], expected['apertures'], fixture)
        self.assertEqual(
            actual['solid_geometry_part_count'], expected['solid_geometry_part_count'], fixture
        )
        self.assertAlmostEqual(
            actual['solid_geometry_total_area'], expected['solid_geometry_total_area'],
            places=FLOAT_PLACES, msg=fixture,
        )
        self.assertEqual(actual['follow_geometry_count'], expected['follow_geometry_count'], fixture)

        self.assertEqual(actual['bounds'] is None, expected['bounds'] is None, fixture)
        if actual['bounds'] is not None:
            for got, want in zip(actual['bounds'], expected['bounds']):
                self.assertAlmostEqual(got, want, places=FLOAT_PLACES, msg=fixture)


def _make_test(fixture):
    def test(self):
        self._assert_matches_baseline(fixture)

    return test


for _fixture in GERBER_FIXTURES:
    _name = 'test_' + _fixture.rsplit('/', 1)[-1].replace('.', '_').replace('-', '_')
    setattr(GerberCharacterizationTestCase, _name, _make_test(_fixture))


if __name__ == '__main__':
    unittest.main()
