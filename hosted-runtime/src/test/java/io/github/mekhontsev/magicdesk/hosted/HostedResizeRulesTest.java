package io.github.mekhontsev.magicdesk.hosted;

import org.junit.Test;
import static org.junit.Assert.*;

public class HostedResizeRulesTest {
    @Test public void terminalGridAndAspectShareOneSizeDecision() {
        var grid = new HostedResizeRules(10, 20, 8, 16, 0, 0, 0, 0, 0, 0);
        var limits = new HostedWindowConstraints(10, 20, 1000, 1000, grid);
        assertEquals(new HostedResizeRules.Size(802, 596), limits.size(800, 600));
        var aspect = new HostedResizeRules(10, 20, 8, 8, 1, 1, 1, 1, 10, 20);
        limits = new HostedWindowConstraints(10, 20, 1000, 1000, aspect);
        assertEquals(new HostedResizeRules.Size(698, 708), limits.size(800, 600));
        for (int w = 1; w < 1000; w += 29) for (int h = 1; h < 1000; h += 41) {
            var result = limits.size(w, h);
            assertEquals(0, (result.width() - 10) % 8);
            assertEquals(0, (result.height() - 20) % 8);
            assertEquals(result.width() - 10, result.height() - 20);
            assertTrue(result.width() >= 10 && result.width() <= 1000);
            assertTrue(result.height() >= 20 && result.height() <= 1000);
        }
    }
    @Test public void impossibleHintsKeepHardLimitsAndDoNotOverflow() {
        var limits = new HostedWindowConstraints(50, 40, 51, 41,
                new HostedResizeRules(100, 100, 8, 8, 1, 1, 1, 1, 100, 100));
        assertEquals(new HostedResizeRules.Size(51, 40), limits.size(200, 2));
        var extreme = new HostedWindowConstraints(1, 1, 100, 100,
                new HostedResizeRules(0, 0, 1, 1, Integer.MAX_VALUE, 1, Integer.MAX_VALUE, 1, 0, 0));
        assertEquals(new HostedResizeRules.Size(100, 1), extreme.size(Integer.MAX_VALUE, Integer.MIN_VALUE));
        assertEquals(new HostedResizeRules.Size(100, 200), HostedWindowConstraints.NONE.size(100, 200));
    }
}
