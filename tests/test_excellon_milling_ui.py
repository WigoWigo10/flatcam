import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

from shapely.geometry import Point

from appObjects.FlatCAMExcellon import ExcellonObject


class ExcellonMillingUiTest(unittest.TestCase):
    @staticmethod
    def fake_object():
        obj = SimpleNamespace(
            app=SimpleNamespace(defaults=SimpleNamespace(report_usage=Mock())),
            read_form=Mock(),
            generate_milling_drills=Mock(),
            generate_milling_slots=Mock(),
        )
        obj._generate_milling_drills_from_ui = (
            lambda: ExcellonObject._generate_milling_drills_from_ui(obj)
        )
        obj._generate_milling_slots_from_ui = (
            lambda: ExcellonObject._generate_milling_slots_from_ui(obj)
        )
        return obj

    def test_3_4mm_hole_with_1mm_end_mill_uses_1_2mm_centerline_radius(self):
        center = Point(17.0, 75.0)

        path = ExcellonObject.milling_drill_path(
            drill=center,
            hole_diameter=3.4,
            tool_diameter=1.0
        )

        self.assertFalse(path.is_empty)
        self.assertTrue(path.is_ring)
        self.assertAlmostEqual(15.8, path.bounds[0], places=6)
        self.assertAlmostEqual(73.8, path.bounds[1], places=6)
        self.assertAlmostEqual(18.2, path.bounds[2], places=6)
        self.assertAlmostEqual(76.2, path.bounds[3], places=6)

    def test_project_string_tool_key_is_resolved_from_integer_ui_key(self):
        tools = {"1": {"tooldia": 3.4}}

        resolved = ExcellonObject.resolve_milling_tool_key(1, tools)

        self.assertEqual("1", resolved)

    def test_mill_drills_ui_error_is_reported_without_escaping_qt_callback(self):
        obj = self.fake_object()
        obj.generate_milling_drills.side_effect = KeyError(1)
        obj.app.inform = SimpleNamespace(emit=Mock())

        ExcellonObject._generate_milling_drills_from_ui(obj)

        obj.app.inform.emit.assert_called_once()
        self.assertIn("Mill Drills failed", obj.app.inform.emit.call_args.args[0])

    def test_mill_drills_is_deferred_until_clicked_signal_returns(self):
        obj = self.fake_object()
        callbacks = []

        with patch('appObjects.FlatCAMExcellon.QtCore.QTimer.singleShot',
                   side_effect=lambda delay, callback: callbacks.append((delay, callback))):
            ExcellonObject.on_generate_milling_button_click(obj)

        obj.read_form.assert_not_called()
        obj.generate_milling_drills.assert_not_called()
        self.assertEqual(1, len(callbacks))
        self.assertEqual(0, callbacks[0][0])

        callbacks[0][1]()

        obj.read_form.assert_called_once_with()
        obj.generate_milling_drills.assert_called_once_with(
            use_thread=False, plot=True, autoselected=None
        )

    def test_mill_slots_is_deferred_until_clicked_signal_returns(self):
        obj = self.fake_object()
        callbacks = []

        with patch('appObjects.FlatCAMExcellon.QtCore.QTimer.singleShot',
                   side_effect=lambda delay, callback: callbacks.append((delay, callback))):
            ExcellonObject.on_generate_milling_slots_button_click(obj)

        obj.read_form.assert_not_called()
        obj.generate_milling_slots.assert_not_called()
        self.assertEqual(1, len(callbacks))
        self.assertEqual(0, callbacks[0][0])

        callbacks[0][1]()

        obj.read_form.assert_called_once_with()
        obj.generate_milling_slots.assert_called_once_with(
            use_thread=False, plot=True, autoselected=None
        )


if __name__ == '__main__':
    unittest.main()
