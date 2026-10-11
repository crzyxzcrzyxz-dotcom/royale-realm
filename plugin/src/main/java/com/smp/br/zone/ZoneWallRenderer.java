package com.smp.br.zone;

import com.smp.br.BattleRoyalePlugin;
import com.smp.br.util.Keys;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-player visual walls: no particles, collisions or changes to map blocks. */
public final class ZoneWallRenderer {
    private final BattleRoyalePlugin plugin;
    private final Map<UUID, List<BlockDisplay>> walls = new HashMap<>();

    public ZoneWallRenderer(BattleRoyalePlugin plugin) { this.plugin = plugin; }

    public void update(Player player, World world, boolean circle, double cx, double cz, double radius) {
        var config = plugin.configs().config();
        if (world == null || !world.equals(player.getWorld()) || !config.getBoolean("zone.wall.enabled", true)) {
            remove(player.getUniqueId());
            return;
        }
        double view = Math.max(16, Math.min(192, config.getDouble("zone.wall.view-distance", 96)));
        double width = Math.max(1, Math.min(8, config.getDouble("zone.wall.panel-width", 4)));
        int limit = Math.max(16, Math.min(256, config.getInt("zone.wall.max-panels-per-player", 128)));
        Location viewer = player.getLocation();
        List<ZoneWallGeometry.Panel> panels = ZoneWallGeometry.panels(circle, cx, cz, radius,
                viewer.getX(), viewer.getZ(), view, width, limit);
        List<BlockDisplay> displays = walls.computeIfAbsent(player.getUniqueId(), id -> new ArrayList<>());
        displays.removeIf(display -> !display.isValid() || !display.getWorld().equals(world));
        while (displays.size() > panels.size()) displays.remove(displays.size() - 1).remove();
        Material material = Material.matchMaterial(config.getString("zone.wall.material", "PURPLE_STAINED_GLASS"));
        if (material == null || !material.isBlock() || material.isAir()) material = Material.PURPLE_STAINED_GLASS;
        for (int i = 0; i < panels.size(); i++) {
            ZoneWallGeometry.Panel panel = panels.get(i);
            Location at = new Location(world, panel.x(), world.getMinHeight(), panel.z());
            BlockDisplay display;
            if (i < displays.size()) {
                display = displays.get(i);
                display.teleport(at);
            } else {
                display = world.spawn(at, BlockDisplay.class, entity -> {
                    entity.setVisibleByDefault(false);
                    entity.setPersistent(false);
                    entity.setGravity(false);
                    entity.setInvulnerable(true);
                    entity.setBillboard(Display.Billboard.FIXED);
                    entity.setBrightness(new Display.Brightness(15, 15));
                    entity.setViewRange(16f);
                    entity.setTeleportDuration(5);
                    entity.setInterpolationDuration(5);
                    entity.getPersistentDataContainer().set(Keys.TEMP, PersistentDataType.BYTE, (byte) 1);
                });
                player.showEntity(plugin, display);
                displays.add(display);
            }
            float height = world.getMaxHeight() - world.getMinHeight();
            float length = (float) panel.width();
            float angle = (float) -Math.atan2(panel.dz(), panel.dx());
            // Center each pane on the damage boundary, rather than offsetting it inward.
            float normalX = (float) (-panel.dz() / panel.width());
            float normalZ = (float) (panel.dx() / panel.width());
            display.setBlock(material.createBlockData());
            display.setDisplayWidth(length * 2);
            display.setDisplayHeight(height * 2);
            display.setInterpolationDelay(0);
            display.setTransformation(new Transformation(
                    new Vector3f((float) -panel.dx() / 2 - normalX * 0.04f, 0,
                            (float) -panel.dz() / 2 - normalZ * 0.04f),
                    new Quaternionf().rotationY(angle), new Vector3f(length + 0.03f, height, 0.08f),
                    new Quaternionf()));
        }
    }

    public void remove(UUID id) {
        List<BlockDisplay> displays = walls.remove(id);
        if (displays != null) displays.forEach(BlockDisplay::remove);
    }

    public void clear() {
        walls.values().forEach(displays -> displays.forEach(BlockDisplay::remove));
        walls.clear();
    }
}