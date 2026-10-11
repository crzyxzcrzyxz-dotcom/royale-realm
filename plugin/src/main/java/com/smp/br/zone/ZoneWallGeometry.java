package com.smp.br.zone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure geometry shared by the renderer and its regression tests. */
public final class ZoneWallGeometry {
    private ZoneWallGeometry() {}

    public record Panel(double x, double z, double dx, double dz) {
        public double width() { return Math.hypot(dx, dz); }
    }

    public static List<Panel> panels(boolean circle, double cx, double cz, double radius,
                                     double px, double pz, double view, double width, int limit) {
        if (!Double.isFinite(radius) || radius <= 0.5 || limit <= 0) return List.of();
        List<Panel> result = new ArrayList<>();
        width = Math.max(1, width);
        if (circle) {
            int count = (int) Math.min(100_000, Math.max(16, Math.ceil(2 * Math.PI * radius / width)));
            double step = 2 * Math.PI / count;
            int nearest = (int) Math.floor(Math.atan2(pz - cz, px - cx) / step);
            // Only visit a bounded arc around the viewer, never scan the entire perimeter.
            int candidates = Math.min(count, Math.max(limit * 2, 32));
            for (int offset = -candidates / 2; offset < candidates - candidates / 2; offset++) {
                double a = (nearest + offset) * step;
                double b = a + step;
                double x1 = cx + Math.cos(a) * radius, z1 = cz + Math.sin(a) * radius;
                double x2 = cx + Math.cos(b) * radius, z2 = cz + Math.sin(b) * radius;
                add(result, new Panel((x1 + x2) / 2, (z1 + z2) / 2, x2 - x1, z2 - z1), px, pz, view);
            }
        } else {
            for (int side = 0; side < 4; side++) {
                boolean horizontal = side < 2;
                double axis = horizontal ? px : pz;
                double center = horizontal ? cx : cz;
                double min = center - radius, max = center + radius;
                double from = Math.max(min, min + Math.floor((axis - view - min) / width) * width);
                double to = Math.min(max, axis + view + width);
                int candidates = 0;
                for (double start = from; start < to && candidates < limit * 2; start += width, candidates++) {
                    double end = Math.min(max, start + width);
                    double fixed = (horizontal ? cz : cx) + (side % 2 == 0 ? -radius : radius);
                    Panel panel = horizontal ? new Panel((start + end) / 2, fixed, end - start, 0)
                            : new Panel(fixed, (start + end) / 2, 0, end - start);
                    add(result, panel, px, pz, view);
                }
            }
        }
        result.sort(Comparator.comparingDouble(p -> Math.hypot(p.x() - px, p.z() - pz)));
        return List.copyOf(result.subList(0, Math.min(limit, result.size())));
    }

    private static void add(List<Panel> panels, Panel panel, double px, double pz, double view) {
        if (Math.hypot(panel.x() - px, panel.z() - pz) <= view + panel.width() / 2) panels.add(panel);
    }
}