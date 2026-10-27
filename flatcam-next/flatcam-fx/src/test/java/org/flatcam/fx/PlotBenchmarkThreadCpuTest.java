package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlotBenchmarkThreadCpuTest {
    @Test void reportsCounterDifferencesAndTheirSamplingWindowsNotGpuTime() {
        var before = new PlotBenchmarkThreadCpu.Snapshot(10,20,true,
                Map.of(1L,new PlotBenchmarkThreadCpu.Counter("QuantumRenderer-0",10_000_000,5_000_000)));
        var after = new PlotBenchmarkThreadCpu.Snapshot(100,110,true,
                Map.of(1L,new PlotBenchmarkThreadCpu.Counter("QuantumRenderer-0",30_000_000,12_000_000)));
        var result = PlotBenchmarkThreadCpu.delta(before,after);
        assertEquals("PLATFORM_THREAD_CPU_NOT_GPU_TIME", result.getString("metric"));
        assertEquals(20, result.getJSONArray("threads").getJSONObject(0).getDouble("cpuMs"));
        assertEquals(7, result.getJSONArray("threads").getJSONObject(0).getDouble("userMs"));
        assertEquals(10, result.getLong("beforeSampleStartNanoTime"));
        assertEquals(110, result.getLong("afterSampleEndNanoTime"));
    }

    @Test void newDeadResetAndMissingCountersAreNotZeroCpuSamples() {
        var before = new PlotBenchmarkThreadCpu.Snapshot(0,0,true,Map.of(
                1L,new PlotBenchmarkThreadCpu.Counter("reset",100,100),
                2L,new PlotBenchmarkThreadCpu.Counter("dead",100,100),
                3L,new PlotBenchmarkThreadCpu.Counter("missing",-1,-1)));
        var after = new PlotBenchmarkThreadCpu.Snapshot(0,0,true,Map.of(
                1L,new PlotBenchmarkThreadCpu.Counter("reset",50,50),
                3L,new PlotBenchmarkThreadCpu.Counter("missing",-1,-1),
                4L,new PlotBenchmarkThreadCpu.Counter("new",100,100)));
        var result = PlotBenchmarkThreadCpu.delta(before,after);
        assertEquals(0,result.getJSONArray("threads").length());
        assertEquals(3,result.getInt("newOrInvalidCounters"));
        assertEquals(1,result.getInt("disappearedCounters"));
    }

    @Test void disabledMeasurementAndMissingUserTimeRemainUnavailable() {
        var disabled = new PlotBenchmarkThreadCpu.Snapshot(0,0,false,Map.of());
        assertEquals("UNAVAILABLE",PlotBenchmarkThreadCpu.delta(disabled,disabled).getString("status"));
        var before = new PlotBenchmarkThreadCpu.Snapshot(0,0,true,Map.of(1L,new PlotBenchmarkThreadCpu.Counter("thread",0,-1)));
        var after = new PlotBenchmarkThreadCpu.Snapshot(0,0,true,Map.of(1L,new PlotBenchmarkThreadCpu.Counter("thread",0,-1)));
        assertTrue(PlotBenchmarkThreadCpu.delta(before,after).getJSONArray("threads").getJSONObject(0).isNull("userMs"));
    }
}
