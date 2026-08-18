package com.smp.br.zone;

import com.smp.br.BattleRoyalePlugin;
import com.smp.br.map.BRMap;
import com.smp.br.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Gerencia a Safe Zone e a Tempestade.
 * Implementa fechamento aleatorio estilo Fortnite e correcao de dano fantasma.
 */
public class ZoneManager {

    private final BattleRoyalePlugin plugin;
    private final BRMap map;

    private Vector center = new Vector(0,0,0);
    private double radius;
    
    private Vector nextCenter = new Vector(0,0,0);
    private double nextRadius;
    
    private Vector startCenter = new Vector(0,0,0);
    private double startRadius;

    private int phase = 0;
    private int seconds = 0;
    private int totalSeconds = 0;
    private Mode mode = Mode.WAITING;
    private BossBar bossBar;

    public enum Mode { WAITING, SHRINKING, FINAL }

    public ZoneManager(BattleRoyalePlugin plugin, BRMap map) {
        this.plugin = plugin;
        this.map = map;
        this.center = new Vector(map.centerX(), 0, map.centerZ());
        this.radius = map.radius();
        this.bossBar = BossBar.bossBar(Text.comp("Safe Zone"), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
    }

    public void start() {
        phase = 0;
        center = new Vector(map.centerX(), 0, map.centerZ());
        radius = map.radius();
        
        // Sincroniza visualmente a borda do mundo se possivel
        if (map.world() != null) {
            WorldBorder wb = map.world().getWorldBorder();
            wb.setCenter(center.getX(), center.getZ());
            wb.setSize(radius * 2);
            wb.setDamageAmount(0); // Dano gerenciado pelo plugin
            wb.setWarningDistance(5);
        }
        
        prepareNextPhase();
    }

    private void prepareNextPhase() {
        phase++;
        mode = Mode.WAITING;
        
        // Configura as fases (exemplo: 1=400, 2=200, 3=100, 4=50, 5=20, 6=0)
        double[] radiusSteps = {map.radius(), map.radius() * 0.7, map.radius() * 0.4, map.radius() * 0.2, 50, 10, 0};
        if (phase >= radiusSteps.length) {
            mode = Mode.FINAL;
            nextRadius = 0;
        } else {
            nextRadius = radiusSteps[phase];
        }

        if (plugin.configs().config().getBoolean("zone.random-zones", true) && nextRadius > 0) {
            pickRandomCenter();
        } else {
            nextCenter = center.clone();
        }

        seconds = plugin.configs().config().getInt("zone.phases." + phase + ".wait", 120);
        totalSeconds = seconds;
        
        startCenter = center.clone();
        startRadius = radius;
        
        plugin.messages().broadcast("zone.new-safe");
    }

    private void pickRandomCenter() {
        double maxDist = radius - nextRadius;
        if (maxDist <= 0) {
            nextCenter = center.clone();
            return;
        }

        int tries = plugin.configs().config().getInt("zone.terrain-tries", 40);
        World world = map.world();
        
        for (int i = 0; i < tries; i++) {
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            double dist = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * maxDist;
            double tx = center.getX() + Math.cos(angle) * dist;
            double tz = center.getZ() + Math.sin(angle) * dist;

            if (world != null && plugin.configs().config().getBoolean("zone.terrain-check", true)) {
                int y = world.getHighestBlockYAt((int)tx, (int)tz);
                if (y > 0 && y < 250) {
                    if (!world.getBlockAt((int)tx, y, (int)tz).isLiquid()) {
                        nextCenter = new Vector(tx, 0, tz);
                        return;
                    }
                }
            } else {
                nextCenter = new Vector(tx, 0, tz);
                return;
            }
        }
        
        nextCenter = center.clone();
    }

    public boolean tickSecond() {
        if (mode == Mode.FINAL && radius <= 0) return false;

        seconds--;
        if (seconds <= 0) {
            if (mode == Mode.WAITING) {
                mode = Mode.SHRINKING;
                seconds = plugin.configs().config().getInt("zone.phases." + phase + ".shrink", 60);
                totalSeconds = seconds;
                plugin.messages().broadcast("zone.shrinking");
                plugin.messages().soundAll("zone-shrink");
            } else {
                prepareNextPhase();
            }
        }
        
        if (mode == Mode.SHRINKING) {
            double elapsed = totalSeconds - seconds;
            double ratio = elapsed / totalSeconds;
            
            center.setX(startCenter.getX() + (nextCenter.getX() - startCenter.getX()) * ratio);
            center.setZ(startCenter.getZ() + (nextCenter.getZ() - startCenter.getZ()) * ratio);
            radius = startRadius + (nextRadius - startRadius) * ratio;
            
            // Sincroniza WorldBorder em tempo real
            if (map.world() != null) {
                WorldBorder wb = map.world().getWorldBorder();
                wb.setCenter(center.getX(), center.getZ());
                wb.setSize(Math.max(0.1, radius * 2));
            }
        }
        
        updateBossBar();
        return true;
    }

    private void updateBossBar() {
        if (!plugin.configs().config().getBoolean("zone.bossbar", true)) return;
        
        String text = plugin.messages().raw(mode == Mode.WAITING ? "zone.bar-waiting" : "zone.bar-shrinking",
                "%time%", Text.time(seconds), "%radius%", String.valueOf((int)radius));
        
        float progress = totalSeconds > 0 ? (float) seconds / totalSeconds : 0;
        progress = Math.max(0, Math.min(progress, 1));

        bossBar.name(Text.comp(text));
        bossBar.progress(progress);
        
        Bukkit.getOnlinePlayers().forEach(p -> p.showBossBar(bossBar));
    }

    public void detach(Player player) {
        if (bossBar != null) player.hideBossBar(bossBar);
    }

    public void stop() {
        if (bossBar != null) {
            Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bossBar));
        }
        if (map.world() != null) {
            map.world().getWorldBorder().reset();
        }
    }

    public void applyStorm(Player player, boolean allowed) {
        if (!allowed) return;
        
        double dist = distanceToZone(player.getLocation());
        double tolerance = plugin.configs().config().getDouble("zone.outside-tolerance", 3.0);
        
        if (dist > tolerance) {
            double damage = plugin.configs().config().getDouble("zone.damage", 2.0);
            player.damage(damage);
            plugin.messages().send(player, "zone.damage-warn");
            plugin.messages().sound(player, "storm-damage");
            
            if (plugin.configs().config().getBoolean("zone.particles", true)) {
                player.getWorld().spawnParticle(Particle.valueOf(plugin.configs().config().getString("zone.particle", "CLOUD")), 
                        player.getLocation().add(0, 1.5, 0), 10, 0.5, 0.5, 0.5, 0.05);
            }
        }
    }

    public double distanceToZone(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().equals(map.world())) return 0;
        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();
        return Math.sqrt(dx * dx + dz * dz) - radius;
    }

    public void showRing(Player player) {
        // O WorldBorder ja faz o trabalho visual, as particulas sao secundarias
        if (!plugin.configs().config().getBoolean("effects.zone-ring", true)) return;
        if (radius <= 0) return;
        
        // Simples indicacao visual ao redor do jogador
        Location loc = player.getLocation();
        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();
        double dist = Math.sqrt(dx*dx + dz*dz);
        
        if (Math.abs(dist - radius) < 10) {
            double angle = Math.atan2(dz, dx);
            for (double a = angle - 0.5; a < angle + 0.5; a += 0.1) {
                double px = center.getX() + Math.cos(a) * radius;
                double pz = center.getZ() + Math.sin(a) * radius;
                player.spawnParticle(Particle.SOUL_FIRE_FLAME, px, loc.getY() + 1.5, pz, 1, 0, 0, 0, 0);
            }
        }
    }

    public double radius() { return radius; }
    public int phase() { return phase; }
    public int totalPhases() { return 6; }
    public Mode mode() { return mode; }
    public double currentX() { return center.getX(); }
    public double currentZ() { return center.getZ(); }
}