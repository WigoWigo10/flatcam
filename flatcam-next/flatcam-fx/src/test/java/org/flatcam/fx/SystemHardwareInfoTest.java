package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SystemHardwareInfoTest {
    @Test void parsesCpuNameWithoutRegistryKeysOrIdentifierNoise() {
        assertEquals("Intel(R) Core(TM) Ultra 7 155H", SystemHardwareInfo.registryCpu(
                "\r\nHKEY_LOCAL_MACHINE\\HARDWARE\\...\r\n    ProcessorNameString    REG_SZ    Intel(R) Core(TM) Ultra 7 155H  \r\n"));
        assertEquals("", SystemHardwareInfo.registryCpu("ERROR: access denied"));
        assertEquals("", SystemHardwareInfo.registryCpu("    Identifier REG_SZ AMD64 Family 6"));
    }

    @Test void memoryUnitsAndMissingMetricsRemainHonest() {
        assertEquals("não disponível", SystemHardwareInfo.memory(-1));
        assertEquals("2.00 GiB (2048 MiB)", SystemHardwareInfo.memory(2L * 1024 * 1024 * 1024));
        assertTrue(SystemHardwareInfo.unavailable().summary().contains("RAM física total: não disponível"));
        assertTrue(SystemHardwareInfo.unavailable().summary().contains("Threads Java (plataforma): não disponível"));
    }

    @Test void physicalRamAndJavaHeapAreSeparateAndCopiedTogether() {
        var hardware = new SystemHardwareInfo("Test CPU", 8, 16L << 30, 4L << 30, 1L << 30, 2L << 30, 6L << 30, 21);
        String summary = AboutInfo.technicalSummary(hardware, GraphicsRuntimeInfo.unavailable("fixture"));
        assertTrue(summary.contains("CPU: Test CPU"));
        assertTrue(summary.contains("RAM física total: 16.00 GiB"));
        assertTrue(summary.contains("Heap Java máximo: 6.00 GiB"));
        assertTrue(summary.contains("não identificado"));
    }

    @Test void realLocalCollectionHasSaneValues() {
        var info = SystemHardwareInfo.collect();
        assertFalse(info.cpu().isBlank()); assertTrue(info.logicalProcessors() > 0);
        assertTrue(info.heapMax() > 0); assertTrue(info.heapUsed() >= 0); assertTrue(info.heapCommitted() >= info.heapUsed());
        assertTrue(info.ramTotal() == -1 || info.ramTotal() > 0);
        assertTrue(info.ramAvailable() == -1 || info.ramAvailable() >= 0);
        assertTrue(info.javaThreads() > 0);
    }
}
