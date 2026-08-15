package com.smp.br.zone;

import com.smp.br.BattleRoyalePlugin;
import com.smp.br.map.BRMap;
import com.smp.br.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Safe Zone + Storm. Gera zonas aleatorias sempre jogaveis, sempre dentro
 * (ou parcialmente dentro) da zona anterior.
 */
public class ZoneManager {

    public enum Mode {WAITING, SHRINKING, FINAL}

    private final BattleRoyalePlugin plugin;
    private final BRMap map;

    private Mode mode = Mode.WAITING;
    private int phase;
    private int seconds;
    private int totalSeconds;

    private Vector center;
    private double radius;
    private Vector nextCenter;
    private double nextRadius;

    private final WorldBorder border;
    private final BossBar bossBar;

    public ZoneManager(BattleRoyalePlugin plugin, BRMap map) {
        this.plugin = plugin;
        this.map = map;
        this.border = Bukkit.createWorldBorder();
        this.bossBar = BossBar.bossBar(Text.comp("&bSafe Zone"), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
    }

    public void start() {
        phase = 0;
        center = new Vector(map.firstZoneX(), 0, map.firstZoneZ());
        radius = map.firstZoneRadius();
        computeNext();
        mode = Mode.WAITING;
        totalSeconds = map.waitSeconds(0);
        seconds = totalSeconds;
        border.setCenter(center.getX(), center.getZ());
        border.setSize(radius * 2);
        border.setWarningDistance(5);
        border.setDamageAmount(0);
        border.setDamageBuffer(0);
    }

    public void attach(Player player) {
        player.setWorldBorder(border);
        if (plugin.configs().config().getBoolean("zone.bossbar", true)) {
            player.showBossBar(bossBar);
        }
    }

    public void detach(Player player) {
        player.setWorldBorder(null);
        player.hideBossBar(bossBar);
    }

    public Mode mode() {
        return mode;
    }

    public int phase() {
        return phase;
    }

    public int totalPhases() {
        return map.zonePhases();
    }

    public double radius() {
        return radius;
    }

    public Vector center() {
        return center.clone();
    }

    public boolean finished() {
        return mode == Mode.FINAL;
    }

    public Location centerLocation() {
        return new Location(map.world(), center.getX(), map.busHeight(), center.getZ());
    }

    public double distanceToZone(Location location) {
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        return Math.max(0, Math.sqrt(dx * dx + dz * dz) - radius);
    }

    public boolean isOutside(Location location) {
        return distanceToZone(location) > 0.5;
    }

    public double currentDamage() {
        List<Double> list = plugin.configs().config().getDoubleList("zone.damage-per-phase");
        if (list.isEmpty()) return 1.0 + phase;
        int index = Math.min(phase, list.size() - 1);
        return list.get(index);
    }

    private void computeNext() {
        int phases = map.zonePhases();
        double finalRadius = map.finalZoneRadius();
        double step = (map.firstZoneRadius() - finalRadius) / phases;
        nextRadius = Math.max(finalRadius, radius - step);

        double free = Math.max(0, radius - nextRadius) * map.zoneDrift();
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double distance = ThreadLocalRandom.current().nextDouble(free + 0.0001);
        double x = center.getX() + Math.cos(angle) * distance;
        double z = center.getZ() + Math.sin(angle) * distance;

        // nunca sair da area jogavel do mapa
        double maxRadius = map.radius() - nextRadius;
        double dx = x - map.centerX();
        double dz = z - map.centerZ();
        double fromMapCenter = Math.sqrt(dx * dx + dz * dz);
        if (maxRadius > 0 && fromMapCenter > maxRadius) {
            double scale = maxRadius / fromMapCenter;
            x = map.centerX() + dx * scale;
            z = map.centerZ() + dz * scale;
        }
        nextCenter = new Vector(x, 0, z);
    }

    /** Executado a cada segundo pela partida. Retorna true enquanto a zona ainda evolui. */
    public boolean tickSecond() {
        if (mode == Mode.FINAL) {
            updateBossBar();
            return false;
        }
        seconds--;
        if (mode == Mode.WAITING) {
            if (seconds <= 0) {
                mode = Mode.SHRINKING;
                totalSeconds = Math.max(1, map.shrinkSeconds(phase));
                seconds = totalSeconds;
                plugin.messages().broadcast("zone.shrinking", "%phase%", String.valueOf(phase + 1), "%total%",
                        String.valueOf(totalPhases()));
                plugin.messages().soundAll("zone-shrink");
            }
        } else {
            double progress = 1.0 - Math.max(0, seconds) / (double) totalSeconds;
            double currentRadius = lerp(radiusStart(), nextRadius, progress);
            double currentX = lerp(centerStart().getX(), nextCenter.getX(), progress);
            double currentZ = lerp(centerStart().getZ(), nextCenter.getZ(), progress);
            radius = currentRadius;
            center = new Vector(currentX, 0, currentZ);
            border.setCenter(currentX, currentZ);
            border.setSize(radius * 2, 1L);

            if (seconds <= 0) {
                radius = nextRadius;
                center = nextCenter.clone();
                border.setCenter(center.getX(), center.getZ());
                border.setSize(radius * 2);
                phase++;
                if (phase >= map.zonePhases() || radius <= map.finalZoneRadius() + 0.01) {
                    mode = Mode.FINAL;
                    plugin.messages().broadcast("zone.final");
                    updateBossBar();
                    return false;
                }
                shiftStart();
                computeNext();
                mode = Mode.WAITING;
                totalSeconds = Math.max(1, map.waitSeconds(phase));
                seconds = totalSeconds;
            }
        }
        updateBossBar();
        return true;
    }

    private Vector startCenter;
    private double startRadius = -1;

    private Vector centerStart() {
        if (startCenter == null) startCenter = center.clone();
        return startCenter;
    }

    private double radiusStart() {
        if (startRadius < 0) startRadius = radius;
        return startRadius;
    }

    private void shiftStart() {
        startCenter = center.clone();
        startRadius = radius;
    }

    private double lerp(double from, double to, double progress) {
        return from + (to - from) * Math.max(0, Math.min(1, progress));
    }

    private void updateBossBar() {
        if (!plugin.configs().config().getBoolean("zone.bossbar", true)) return;
        String path = mode == Mode.SHRINKING ? "zone.bossbar-shrinking" : "zone.bossbar-waiting";
        String text = plugin.messages().raw(path,
                "%phase%", String.valueOf(Math.min(phase + 1, totalPhases())),
                "%total%", String.valueOf(totalPhases()),
                "%time%", String.valueOf(Math.max(0, seconds)),
                "%radius%", String.valueOf((int) radius));
        bossBar.name(Text.comp(text));
        float progress = totalSeconds <= 0 ? 0f : Math.max(0f, Math.min(1f, seconds / (float) totalSeconds));
        bossBar.progress(progress);
        bossBar.color(mode == Mode.SHRINKING ? BossBar.Color.RED : BossBar.Color.BLUE);
    }

    /** Avisos, particulas e dano para quem esta fora da zona. */
    public void applyStorm(Player player) {
        Location location = player.getLocation();
        double distance = distanceToZone(location);
        if (distance <= 0.5) {
            if (plugin.configs().config().getBoolean("zone.actionbar", true) && mode != Mode.FINAL) {
                plugin.messages().actionBar(player, plugin.messages().raw(
                        mode == Mode.SHRINKING ? "zone.shrinking" : "zone.waiting",
                        "%time%", String.valueOf(Math.max(0, seconds)),
                        "%phase%", String.valueOf(Math.min(phase + 1, totalPhases())),
                        "%total%", String.valueOf(totalPhases())));
            }
            return;
        }
        plugin.messages().actionBar(player,
                plugin.messages().raw("zone.outside", "%distance%", String.valueOf((int) distance)));
        plugin.messages().sound(player, "storm-damage");
        if (plugin.configs().config().getBoolean("zone.particles", true)) {
            Particle particle = particle();
            player.getWorld().spawnParticle(particle, location.clone().add(0, 1, 0), 12, 0.5, 1, 0.5, 0.02);
        }
        player.damage(currentDamage());
    }

    private Particle particle() {
        try {
            return Particle.valueOf(plugin.configs().config().getString("zone.particle", "CLOUD").toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Particle.CLOUD;
        }
    }

    public void showRing(Player player) {
        if (!plugin.configs().config().getBoolean("zone.particles", true)) return;
        Location location = player.getLocation();
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (Math.abs(distance - radius) > 30) return;
        for (int i = 0; i < 24; i++) {
            double angle = (Math.PI * 2 / 24) * i;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            double ddx = x - location.getX();
            double ddz = z - location.getZ();
            if (ddx * ddx + ddz * ddz > 900) continue;
            Location point = new Location(location.getWorld(), x, location.getY() + 1, z);
            player.spawnParticle(Particle.END_ROD, point, 1, 0, 1.5, 0, 0);
        }
    }

    public void cleanup() {
        Bukkit.getOnlinePlayers().forEach(this::detach);
    }
}
