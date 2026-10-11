package com.smp.br.zone;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZoneWallGeometryTest {
    @Test void circularWallFollowsSafeBoundary() {
        var panels = ZoneWallGeometry.panels(true, 20, -30, 400, 419, -30, 96, 4, 128);
        assertFalse(panels.isEmpty());
        for (var panel : panels) {
            assertEquals(400, Math.hypot(panel.x() - panel.dx() / 2 - 20,
                    panel.z() - panel.dz() / 2 + 30), 0.00001);
            assertEquals(400, Math.hypot(panel.x() + panel.dx() / 2 - 20,
                    panel.z() + panel.dz() / 2 + 30), 0.00001);
        }
    }

    @Test void squareWallFollowsSafeBoundary() {
        var panels = ZoneWallGeometry.panels(false, 20, -30, 400, 419, 369, 96, 4, 128);
        assertFalse(panels.isEmpty());
        for (var panel : panels) {
            assertEquals(400, Math.max(Math.abs(panel.x() - 20), Math.abs(panel.z() + 30)), 0.00001);
        }
    }

    @Test void shrinkingWallUsesUpdatedCenterAndRadius() {
        var panels = ZoneWallGeometry.panels(true, 80, 90, 50, 130, 90, 96, 4, 128);
        assertFalse(panels.isEmpty());
        assertTrue(panels.stream().allMatch(p -> Math.hypot(p.x() - 80, p.z() - 90) <= 50));
    }

    @Test void rendererHasBoundedPanelCount() {
        assertEquals(16, ZoneWallGeometry.panels(true, 0, 0, 400, 400, 0, 192, 1, 16).size());
    }

    @Test void distantAndClosedWallsHaveNoPanels() {
        assertTrue(ZoneWallGeometry.panels(true, 0, 0, 400, 0, 0, 96, 4, 128).isEmpty());
        assertTrue(ZoneWallGeometry.panels(false, 0, 0, 0, 0, 0, 96, 4, 128).isEmpty());
    }
}