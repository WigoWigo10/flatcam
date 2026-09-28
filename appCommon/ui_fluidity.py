"""Opt-in GUI event-loop heartbeat shared by the legacy application's profiler.

This measures time between callbacks on the UI thread, not GPU presentation FPS.
The JavaFX counterpart uses the same 16 ms timer and 10 s reporting window.
"""

import math


class UiFluidityMetrics:
    def __init__(self, app_name, window_seconds=10.0):
        self.app_name = app_name
        self.window_seconds = window_seconds
        self.reset()

    def reset(self):
        self._window_start = None
        self._last_tick = None
        self._intervals = []

    def tick(self, now_seconds):
        """Return a report at the end of a window, otherwise None."""
        if self._last_tick is None:
            self._last_tick = now_seconds
            self._window_start = now_seconds
            return None

        interval_ms = (now_seconds - self._last_tick) * 1000.0
        self._last_tick = now_seconds
        if interval_ms < 0:
            self.reset()
            return None
        self._intervals.append(interval_ms)
        elapsed = now_seconds - self._window_start
        if elapsed < self.window_seconds:
            return None

        intervals = sorted(self._intervals)
        count = len(intervals)

        def percentile(fraction):
            return intervals[max(0, math.ceil(fraction * count) - 1)]

        report = ("[UI-FLUIDITY] app=%s window_s=%.1f samples=%d "
                  "p50_ms=%.1f p95_ms=%.1f p99_ms=%.1f max_ms=%.1f "
                  "gaps50=%d gaps100=%d gaps250=%d" % (
                      self.app_name, elapsed, count, percentile(0.50),
                      percentile(0.95), percentile(0.99), intervals[-1],
                      sum(value >= 50 for value in intervals),
                      sum(value >= 100 for value in intervals),
                      sum(value >= 250 for value in intervals)))
        self._window_start = now_seconds
        self._intervals = []
        return report
