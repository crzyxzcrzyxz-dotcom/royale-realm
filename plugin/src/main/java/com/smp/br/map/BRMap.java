package com.smp.br.map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class BRMap {

    private final String id;
    private final ConfigurationSection section;

    public BRMap(String id, ConfigurationSection section) {
        this.id = id;
        this.section = section;
    }

    public String id() {
        return id;
    }

    public ConfigurationSection section() {
        return section;
    }

    public String displayName() {
        return section.getString("display-name", id);
    }

    public boolean enabled() {
        return section.getBoolean("enabled", true);
    }

    public String worldName() {
        return section.getString("world", "world");
    }

    public World world() {
        return Bukkit.getWorld(worldName());
    }

    public boolean isReady() {
        return enabled() && world() != null;
    }

    public Location start() {
        World world = world();
        if (world == null) return null;
        return new Location(world,
                section.getDouble("start.x", 0.5),
                section.getDouble("start.y", 100),
                section.getDouble("start.z", 0.5),
                (float) section.getDouble("start.yaw", 0),
                (float) section.getDouble("start.pitch", 0));
    }

    public double centerX() {
        return section.getDouble("center.x", 0);
    }

    public double centerZ() {
        return section.getDouble("center.z", 0);
    }

    public Location center() {
        World world = world();
        if (world == null) return null;
        return new Location(world, centerX(), world.getHighestBlockYAt((int) centerX(), (int) centerZ()) + 1,
                centerZ());
    }

    public double radius() {
        return section.getDouble("radius", 400);
    }

    public Vector busStart() {
        return new Vector(section.getDouble("bus.start.x", -400), section.getDouble("bus.height", 170),
                section.getDouble("bus.start.z", -400));
    }

    public Vector busEnd() {
        return new Vector(section.getDouble("bus.end.x", 400), section.getDouble("bus.height", 170),
                section.getDouble("bus.end.z", 400));
    }

    public double busHeight() {
        return section.getDouble("bus.height", 170);
    }

    public double busSpeed() {
        return section.getDouble("bus.speed", 0.9);
    }

    public int busDurationSeconds() {
        return section.getInt("bus.duration-seconds", 0);
    }

    public double firstZoneX() {
        return section.getDouble("zone.first.x", centerX());
    }

    public double firstZoneZ() {
        return section.getDouble("zone.first.z", centerZ());
    }

    public double firstZoneRadius() {
        return section.getDouble("zone.first.radius", radius() * 0.95);
    }

    public double finalZoneRadius() {
        return section.getDouble("zone.final-radius", 8);
    }

    public int zonePhases() {
        return Math.max(1, section.getInt("zone.phases", 6));
    }

    public double zoneDrift() {
        return Math.max(0, Math.min(1, section.getDouble("zone.drift", 0.7)));
    }

    public int waitSeconds(int phaseIndex) {
        return listValue(section.getIntegerList("zone.wait-seconds"), phaseIndex, 45);
    }

    public int shrinkSeconds(int phaseIndex) {
        return listValue(section.getIntegerList("zone.shrink-seconds"), phaseIndex, 45);
    }

    public List<Vector> hotZones() {
        List<Vector> result = new ArrayList<>();
        List<?> raw = section.getList("hot-zones");
        if (raw == null) return result;
        for (Object entry : raw) {
            if (entry instanceof java.util.Map<?, ?> map) {
                Object x = map.get("x");
                Object z = map.get("z");
                if (x instanceof Number nx && z instanceof Number nz) {
                    result.add(new Vector(nx.doubleValue(), 0, nz.doubleValue()));
                }
            }
        }
        return result;
    }

    private int listValue(List<Integer> list, int index, int fallback) {
        if (list == null || list.isEmpty()) return fallback;
        if (index < list.size()) return list.get(index);
        return list.get(list.size() - 1);
    }
}
