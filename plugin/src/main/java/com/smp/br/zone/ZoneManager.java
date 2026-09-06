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
 * Safe Zone + Storm.
 *
 * IMPORTANTE: a zona e QUADRADA, exatamente igual a WorldBorder do Minecraft.
 * Isso elimina o bug historico de "tomar dano dentro da zona": antes o dano
 * usava um circulo e a barreira desenhada era um quadrado.
 *
 * A WorldBorder nunca e usada como parede: ela fica sempre no tamanho do mapa
 * (ou desligada), para que o jogador possa entrar e sair da zona quando quiser.
 * A zona em si e desenhada com particulas.
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
        radius = defaultInitialRadius();

        if (map.section().getBoolean("zone.initial.configured", false)) {
            center = new Vector(map.firstZoneX(), 0, map.firstZoneZ());
            radius = Math.min(map.firstZoneRadius(), map.radius());
        }
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

    /** Raio inicial da zona: absoluto, ou multiplicador do raio do mapa. */
    private double defaultInitialRadius() {
        double absolute = plugin.configs().config().getDouble("zone.initial.radius", -1);
        double multiplier = Math.max(0.1, plugin.configs().config().getDouble("zone.initial.radius-multiplier", 1.0));
        double value = absolute > 0 ? absolute : map.radius() * multiplier;
        return Math.min(value, map.radius());
    }

    /**
     * A barreira e 100% do plugin (particulas + dano). A WorldBorder do Minecraft
     * so entra como moldura opcional do MAPA (nunca da zona) e vem DESLIGADA,
     * porque ela e sempre quadrada e empurra o jogador.
     */
    private void applyBorder() {
        if (!useWorldBorder()) return;
        double size = (map.radius() + plugin.configs().config().getDouble("zone.worldborder-margin", 24)) * 2;
        border.setCenter(map.centerX(), map.centerZ());
        border.setSize(Math.max(1.0, size));
        border.setWarningDistance(0);
        border.setWarningTime(0);
        border.setDamageAmount(0);
        border.setDamageBuffer(1_000_000);
    }

    private boolean useWorldBorder() {
        return plugin.configs().config().getBoolean("zone.worldborder", false);
    }

    /** SQUARE (padrao) ou CIRCLE: formato da barreira customizada. */
    private boolean circular() {
        return "CIRCLE".equalsIgnoreCase(plugin.configs().config().getString("zone.shape", "SQUARE"));
    }

    public void attach(Player player) {
        if (useWorldBorder()) {
            player.setWorldBorder(border);
        }
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

    /**
     * Distancia ate a borda da zona (0 = dentro). Usa exatamente a mesma
     * geometria da parede desenhada, seja ela QUADRADA ou REDONDA - e isso que
     * impede o antigo bug de tomar dano dentro da safe zone.
     */
    public double distanceToZone(Location location) {
        World world = map.world();
        if (world == null || location.getWorld() == null) return 0;
        if (!world.equals(location.getWorld())) return 0;
        double dx = Math.abs(location.getX() - center.getX());
        double dz = Math.abs(location.getZ() - center.getZ());
        double distance = circular() ? Math.sqrt(dx * dx + dz * dz) : Math.max(dx, dz);
        return Math.max(0, distance - radius);
    }

    public double tolerance() {
        return Math.max(1.5, plugin.configs().config().getDouble("zone.outside-tolerance", 3.0));
    }

    public boolean isOutside(Location location) {
        return distanceToZone(location) > tolerance();
    }

    /** Dano FIXO da tempestade (padrao: 1 coracao). */
    public double currentDamage() {
        return Math.max(0.5, plugin.configs().config().getDouble("zone.damage", 2.0));
    }

    // ------------------------------------------------------------------
    // Proxima zona
    // ------------------------------------------------------------------

    private void computeNext() {
        int phases = map.zonePhases();
        boolean closeAll = plugin.configs().config().getBoolean("zone.close-completely", true);
        double finalRadius = closeAll ? 0.0 : Math.max(0, map.finalZoneRadius());
        int remaining = Math.max(1, phases - phase);
        double step = (radius - finalRadius) / remaining;
        nextRadius = Math.max(finalRadius, radius - step);

        // ultima fase: fecha no destino final configurado
        if (phase + 1 >= phases) {
            nextRadius = finalRadius;
            nextCenter = finalCenter();
            return;
        }

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
            if (!plugin.configs().config().getBoolean("zone.terrain-check", true) || hasTerrain(candidate)) {
                best = candidate;
                break;
            }
        }
        nextCenter = best;
    }

    /** RANDOM = onde a zona ja esta indo | CENTER = centro do mapa | ZERO = coordenada 0,0. */
    private Vector finalCenter() {
        String target = plugin.configs().config().getString("zone.final-center", "RANDOM");
        if ("ZERO".equalsIgnoreCase(target)) return new Vector(0, 0, 0);
        if ("CENTER".equalsIgnoreCase(target)) return new Vector(map.centerX(), 0, map.centerZ());
        return center.clone();
    }

    private Vector clampToMap(Vector position, double zoneRadius) {
        double maxOffset = Math.max(0, map.radius() - zoneRadius);
        double x = clamp(position.getX(), map.centerX() - maxOffset, map.centerX() + maxOffset);
        double z = clamp(position.getZ(), map.centerZ() - maxOffset, map.centerZ() + maxOffset);
        return new Vector(x, 0, z);
    }

    private double clamp(double value, double min, double max) {
        if (min > max) return (min + max) / 2;
        return Math.max(min, Math.min(max, value));
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

            if (seconds <= 0) {
                radius = nextRadius;
                center = nextCenter.clone();
                phase++;
                boolean closeAll = plugin.configs().config().getBoolean("zone.close-completely", true);
                double limit = closeAll ? 0.05 : map.finalZoneRadius() + 0.01;
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
        if (world == null || !world.equals(location.getWorld())) return;
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

    /**
     * Desenha a PAREDE da zona perto do jogador, com a mesma geometria usada
     * para o dano. Formato conforme "zone.shape": SQUARE ou CIRCLE.
     */
    public void showRing(Player player) {
        if (!plugin.configs().config().getBoolean("zone.particles", true)) return;
        if (!plugin.configs().config().getBoolean("effects.zone-ring", true)) return;
        if (map.world() != null && !map.world().equals(player.getWorld())) return;
        if (radius <= 0.5) return;

        Location location = player.getLocation();
        double view = Math.max(8, plugin.configs().config().getDouble("zone.wall-view-distance", 48));
        double spacing = Math.max(0.5, plugin.configs().config().getDouble("zone.wall-spacing", 2.0));
        int height = Math.max(1, plugin.configs().config().getInt("zone.wall-height", 5));

        if (circular()) {
            double step = spacing / Math.max(1.0, radius); // radianos por ponto
            for (double angle = 0; angle < Math.PI * 2; angle += step) {
                double x = center.getX() + Math.cos(angle) * radius;
                double z = center.getZ() + Math.sin(angle) * radius;
                drawWallPoint(player, location, x, z, view, height);
            }
            return;
        }

        double minX = center.getX() - radius;
        double maxX = center.getX() + radius;
        double minZ = center.getZ() - radius;
        double maxZ = center.getZ() + radius;

        for (double x = minX; x <= maxX; x += spacing) {
            drawWallPoint(player, location, x, minZ, view, height);
            drawWallPoint(player, location, x, maxZ, view, height);
        }
        for (double z = minZ; z <= maxZ; z += spacing) {
            drawWallPoint(player, location, minX, z, view, height);
            drawWallPoint(player, location, maxX, z, view, height);
        }
    }

    private void drawWallPoint(Player player, Location from, double x, double z, double view, int height) {
        double dx = x - from.getX();
        double dz = z - from.getZ();
        if (dx * dx + dz * dz > view * view) return;
        for (int h = 0; h < height; h++) {
            Location point = new Location(from.getWorld(), x, from.getY() + h * 1.5, z);
            player.spawnParticle(Particle.END_ROD, point, 1, 0, 0.4, 0, 0);
        }
    }

    public double initialRadius() {
        return initialRadius;
    }

    public void cleanup() {
        Bukkit.getOnlinePlayers().forEach(this::detach);
    }
}
