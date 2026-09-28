import unittest

from appCommon.ui_fluidity import UiFluidityMetrics


class UiFluidityMetricsTest(unittest.TestCase):
    def test_reports_tail_latencies_and_stalls(self):
        metrics = UiFluidityMetrics('python', window_seconds=0.30)
        self.assertIsNone(metrics.tick(1.0))
        self.assertIsNone(metrics.tick(1.016))
        self.assertIsNone(metrics.tick(1.032))
        report = metrics.tick(1.300)
        self.assertIn('app=python', report)
        self.assertIn('samples=3', report)
        self.assertIn('p50_ms=16.0', report)
        self.assertIn('p95_ms=268.0', report)
        self.assertIn('max_ms=268.0', report)
        self.assertIn('gaps50=1 gaps100=1 gaps250=1', report)

    def test_reset_discards_hidden_window_gap(self):
        metrics = UiFluidityMetrics('python', window_seconds=0.02)
        metrics.tick(1.0)
        metrics.reset()
        self.assertIsNone(metrics.tick(100.0))
        report = metrics.tick(100.021)
        self.assertIn('samples=1', report)
        self.assertIn('gaps50=0', report)


if __name__ == '__main__':
    unittest.main()
