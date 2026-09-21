import unittest
from types import SimpleNamespace

from shapely.geometry import box, MultiPolygon
from shapely.ops import unary_union

from camlib import Geometry, geometry_parts
from appTools.ToolNCC import NonCopperClear


class _Inform:
    def __init__(self):
        self.messages = []

    def emit(self, message):
        self.messages.append(message)


class NccShapely2Test(unittest.TestCase):
    def setUp(self):
        self.geometry = object.__new__(Geometry)
        self.geometry.app = SimpleNamespace(abort_flag=False)
        self.geometry.plot_temp_shapes = lambda *args, **kwargs: None

        # The narrow bridge disappears after the first inward buffer. This
        # reproduces the Polygon -> MultiPolygon transition seen on PCB NCC.
        self.split_polygon = unary_union([
            box(0, 0, 10, 10),
            box(12, 0, 22, 10),
            box(9, 4.6, 13, 5.4),
        ])

    def _assert_linear_paths(self, storage):
        self.assertIsNotNone(storage)
        paths = list(storage.get_objects())
        self.assertTrue(paths)
        self.assertTrue(all(path.geom_type in {'LineString', 'LinearRing'} for path in paths))

    def test_all_clearing_methods_accept_multipart_results(self):
        methods = (
            self.geometry.clear_polygon,
            self.geometry.clear_polygon2,
            self.geometry.clear_polygon3,
        )
        for method in methods:
            with self.subTest(method=method.__name__):
                storage = method(
                    self.split_polygon,
                    1.0,
                    64,
                    overlap=0.15,
                    connect=False,
                    contour=True,
                    prog_plot=False,
                )
                self._assert_linear_paths(storage)

    def test_geometry_parts_recursively_flattens_multipart_geometry(self):
        nested = [MultiPolygon([box(0, 0, 1, 1), box(2, 0, 3, 1)])]
        parts = geometry_parts(nested)
        self.assertEqual(2, len(parts))
        self.assertTrue(all(part.geom_type == 'Polygon' for part in parts))

    def test_combo_continues_after_a_strategy_raises(self):
        good_storage = SimpleNamespace(objects=[object()], get_objects=lambda: iter([box(0, 0, 1, 1).exterior]))

        class FakeNcc:
            circle_steps = 64
            app = SimpleNamespace(inform=_Inform())

            @staticmethod
            def clear_polygon3(*args, **kwargs):
                raise RuntimeError('lines failed')

            @staticmethod
            def clear_polygon2(*args, **kwargs):
                return good_storage

            @staticmethod
            def clear_polygon(*args, **kwargs):
                raise AssertionError('standard fallback should not be reached')

        result = NonCopperClear.clear_polygon_worker(
            FakeNcc(), self.split_polygon, 1.0, 3, 0.15, False, True, False)
        self.assertEqual(1, len(result))

    def test_board_reference_positive_margin_is_clamped(self):
        inform = _Inform()
        fake_ncc = SimpleNamespace(app=SimpleNamespace(inform=inform))
        board = SimpleNamespace(
            kind='geometry',
            options={'name': 'renamed-board', 'is_board_area': True},
        )
        margin = NonCopperClear.constrain_reference_margin(fake_ncc, 2, board, 1.0)
        self.assertEqual(0.0, margin)
        self.assertTrue(inform.messages)

    def test_envelope_inversion_handles_multipolygon(self):
        source = MultiPolygon([box(0, 0, 1, 1), box(2, 0, 3, 1)])

        class FakeNcc:
            geometry_parts = staticmethod(NonCopperClear.geometry_parts)

            @staticmethod
            def isolation_geometry(*args, **kwargs):
                return source

        inverted = NonCopperClear.generate_envelope(FakeNcc(), 0.1, 1)
        self.assertEqual('MultiPolygon', inverted.geom_type)
        self.assertEqual(2, len(inverted.geoms))
        self.assertTrue(all(poly.exterior.is_ccw != original.exterior.is_ccw
                            for poly, original in zip(inverted.geoms, source.geoms)))


if __name__ == '__main__':
    unittest.main()
