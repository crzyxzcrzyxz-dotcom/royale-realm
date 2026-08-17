package com.smp.br.zone;

import com.smp.br.BattleRoyalePlugin;
import com.smp.br.map.BRMap;
import com.smp.br.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Safe Zone + Storm estilo Fortnite: as zonas fecham em locais ALEATORIOS do
 * mapa (sempre em terreno jogavel) e podem fechar por completo.
 * O dano da tempestade e fixo e configuravel.
 */
public class ZoneManager {

    public enum Mode {WAITING, SHRINKING, FINAL}

    private final BattleRoyalePlugin plugin;
    private final BRMap map;

    private Mode mode = Mode.WAITING;
    private int phase;
    private int seconds;
    private int totalSeconds;

    private Vector center = new Vector();
    private double radius;
    private Vector nextCenter;
    private double nextRadius;

    private Vector startCenter;
    private double startRadius;
    private double initialRadius;

    private final WorldBorder border;
    private final BossBar bossBar;

    public ZoneManager(BattleRoyalePlugin plugin, BRMap map) {
        this.plugin = plugin;
        this.map = map;
        this.border = Bukkit.createWorldBorder();
        this.bossBar = BossBar.bossBar(Text.comp("&bSafe Zone"), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
    }

    // ------------------------------------------------------------------

    public void start() {
        phase = 0;
        center = new Vector(map.centerX(), 0, map.centerZ());
        radius = Math.min(map.firstZoneRadius(), map.radius());
        if (radius <= 0) radius = map.radius();
        initialRadius = radius;
        startCenter = center.clone();
        startRadius = radius;
        computeNext();
        mode = Mode.WAITING;
        totalSeconds = Math.max(1, map.waitSeconds(0));
        seconds = totalSeconds;
        applyBorder();
    }

    private void applyBorder() {
        border.setCenter(center.getX(), center.getZ());
        border.setSize(Math.max(1.0, radius * 2));
        border.setWarningDistance(6);
        border.setWarningTime(0);
        // a borda e apenas visual: TODO o dano vem do nosso proprio calculo
        border.setDamageAmount(0);
        border.setDamageBuffer(1_000_000);
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
        World world = map.world();
        if (world != null && !world.equals(location.getWorld())) return 0;
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        return Math.max(0, Math.sqrt(dx * dx + dz * dz) - radius);
    }

    public double tolerance() {
        return Math.max(0.5, plugin.configs().config().getDouble("zone.outside-tolerance", 3.0));
    }

    public boolean isOutside(Location location) {
        return distanceToZone(location) > tolerance();
    }

    /** Dano FIXO da tempestade (padrao: 1 coracao). */
    public double currentDamage() {
        return Math.max(0.5, plugin.configs().config().getDouble("zone.damage", 2.0));
    }

    // ------------------------------------------------------------------
    // Proxima zona (aleatoria e em terreno valido)
    // ------------------------------------------------------------------

    private void computeNext() {
        int phases = map.zonePhases();
        boolean closeAll = plugin.configs().config().getBoolean("zone.close-completely", true);
        double finalRadius = closeAll ? 0.0 : Math.max(0, map.finalZoneRadius());
        int remaining = Math.max(1, phases - phase);
        double step = (radius - finalRadius) / remaining;
        nextRadius = Math.max(finalRadius, radius - step);

        boolean random = plugin.configs().config().getBoolean("zone.random-zones", true);
        double free = Math.max(0, radius - nextRadius);
        if (!random) free *= map.zoneDrift();

        int tries = Math.max(1, plugin.configs().config().getInt("zone.terrain-tries", 40));
        Vector best = center.clone();
        for (int i = 0; i < tries; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double distance = free <= 0 ? 0 : Math.sqrt(ThreadLocalRandom.current().nextDouble()) * free;
            double x = center.getX() + Math.cos(angle) * distance;
            double z = center.getZ() + Math.sin(angle) * distance;
            Vector candidate = clampToMap(new Vector(x, 0, z), nextRadius);
            if (i == 0) best = candidate;
            if (!plugin.configs().config().getBoolean("zone.terrain-check", true) || hasTerrain(candidate)) {
                best = candidate;
                break;
            }
        }
        nextCenter = best;
    }

    private Vector clampToMap(Vector position, double zoneRadius) {
        double maxRadius = Math.max(0, map.radius() - zoneRadius);
        double dx = position.getX() - map.centerX();
        double dz = position.getZ() - map.centerZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (maxRadius <= 0 || distance <= maxRadius) return position;
        double scale = maxRadius / distance;
        return new Vector(map.centerX() + dx * scale, 0, map.centerZ() + dz * scale);
    }

    /** Verifica se ha terreno solido jogavel no centro candidato. */
    private boolean hasTerrain(Vector position) {
        World world = map.world();
        if (world == null) return true;
        int x = (int) Math.floor(position.getX());
        int z = (int) Math.floor(position.getZ());
        int y = world.getHighestBlockYAt(x, z);
        if (y <= world.getMinHeight() + 1) return false;
        Block block = world.getBlockAt(x, y, z);
        Material type = block.getType();
        if (type.isAir()) return false;
        if (type == Material.WATER || type == Material.LAVA) return false;
        return block.isSolid() || type == Material.SNOW || type == Material.GRASS_BLOCK;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    /** Executado a cada segundo pela partida. Retorna true enquanto a zona evolui. */
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
                startCenter = center.clone();
                startRadius = radius;
                plugin.messages().broadcast("zone.shrinking", "%phase%", String.valueOf(phase + 1), "%total%",
                        String.valueOf(totalPhases()));
                plugin.messages().soundAll("zone-shrink");
            }
        } else {
            double progress = 1.0 - Math.max(0, seconds) / (double) totalSeconds;
            radius = lerp(startRadius, nextRadius, progress);
            center = new Vector(lerp(startCenter.getX(), nextCenter.getX(), progress), 0,
                    lerp(startCenter.getZ(), nextCenter.getZ(), progress));
            // a borda visual acompanha EXATAMENTE o calculo interno
            applyBorder();

            if (seconds <= 0) {
                radius = nextRadius;
                center = nextCenter.clone();
                applyBorder();
                phase++;
                boolean closeAll = plugin.configs().config().getBoolean("zone.close-completely", true);
                double limit = closeAll ? 0.5 : map.finalZoneRadius() + 0.01;
                if (phase >= map.zonePhases() || radius <= limit) {
                    mode = Mode.FINAL;
                    plugin.messages().broadcast("zone.final");
                    updateBossBar();
                    return false;
                }
                startCenter = center.clone();
                startRadius = radius;
                computeNext();
                mode = Mode.WAITING;
                totalSeconds = Math.max(1, map.waitSeconds(phase));
                seconds = totalSeconds;
            }
        }
        updateBossBar();
        return true;
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

    // ------------------------------------------------------------------
    // Tempestade
    // ------------------------------------------------------------------

    public void applyStorm(Player player) {
        applyStorm(player, true);
    }

    public void applyStorm(Player player, boolean damageAllowed) {
        Location location = player.getLocation();
        World world = map.world();
        if (world != null && !world.equals(location.getWorld())) return;
        double distance = distanceToZone(location);
        if (distance <= tolerance()) {
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
        if (!damageAllowed) return;
        plugin.messages().sound(player, "storm-damage");
        if (plugin.configs().config().getBoolean("zone.particles", true)) {
            player.getWorld().spawnParticle(particle(), location.clone().add(0, 1, 0), 14, 0.5, 1, 0.5, 0.02);
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
        if (!plugin.configs().config().getBoolean("effects.zone-ring", true)) return;
        if (map.world() != null && !map.world().equals(player.getWorld())) return;
        if (radius <= 0.5) return;
        Location location = player.getLocation();
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (Math.abs(distance - radius) > 40) return;
        for (int i = 0; i < 32; i++) {
            double angle = (Math.PI * 2 / 32) * i;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            double ddx = x - location.getX();
            double ddz = z - location.getZ();
            if (ddx * ddx + ddz * ddz > 1600) continue;
            Location point = new Location(location.getWorld(), x, location.getY() + 1, z);
            player.spawnParticle(Particle.END_ROD, point, 1, 0, 1.6, 0, 0);
        }
    }

    public double initialRadius() {
        return initialRadius;
    }

    public void cleanup() {
        Bukkit.getOnlinePlayers().forEach(this::detach);
    }
}
