package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.*;
import org.junit.Test;

public final class SystemCpuUsageTest {
    @Test public void firstSampleFailuresCounterResetsAndRecoveryStayExplicit() {
        var usage = new SystemCpuUsage();
        assertEquals(-1, usage.sample(new SystemCpuSnapshot(100, 20)), 0);
        assertEquals(.5, usage.sample(new SystemCpuSnapshot(200, 70)), .00001);
        assertEquals(-1, usage.sample(SystemCpuSnapshot.UNKNOWN), 0);
        assertEquals(-1, usage.sample(new SystemCpuSnapshot(300, 90)), 0);
        assertEquals(0, usage.sample(new SystemCpuSnapshot(400, 190)), 0);
        assertEquals(-1, usage.sample(new SystemCpuSnapshot(2, 1)), 0);
        assertEquals(1, usage.sample(new SystemCpuSnapshot(10, 1)), 0);
        assertEquals(-1, usage.sample(new SystemCpuSnapshot(11, 9)), 0);
    }
}
