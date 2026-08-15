package com.smp.br.bus;

import com.smp.br.BattleRoyalePlugin;
import com.smp.br.map.BRMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Battle Bus: percorre gradualmente a rota configurada carregando os jogadores.
 */
public class BusManager {

    private final BattleRoyalePlugin plugin;
    private final BRMap map;

    private final List<ArmorStand> body = new ArrayList<>();
    private final Set<UUID> passengers = new HashSet<>();

    private Vector start;
    private Vector direction;
    private double length;
    private double speed;
    private double travelled;
    private boolean running;

    public BusManager(BattleRoyalePlugin plugin, BRMap map) {
        this.plugin = plugin;
        this.map = map;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean hasPassenger(UUID uuid) {
        return passengers.contains(uuid);
    }

    public Set<UUID> passengers() {
        return passengers;
    }

    public Location currentLocation() {
        World world = map.world();
        if (world == null || direction == null) return null;
        Vector position = start.clone().add(direction.clone().multiply(travelled));
        return new Location(world, position.getX(), map.busHeight(), position.getZ());
    }

    public void start(List<Player> players) {
        World world = map.world();
        if (world == null) return;
        start = map.busStart();
        Vector end = map.busEnd();
        Vector delta = end.clone().subtract(start);
        delta.setY(0);
        length = delta.length();
        direction = length == 0 ? new Vector(1, 0, 0) : delta.clone().normalize();
        int duration = map.busDurationSeconds();
        speed = duration > 0 ? length / (duration * 20.0) : map.busSpeed();
        if (speed <= 0) speed = 0.9;
        travelled = 0;
        running = true;

        spawnBody(world);

        for (Player player : players) {
            board(player);
        }
    }

    private void spawnBody(World world) {
        Material material = Material.matchMaterial(
                plugin.configs().config().getString("bus.block", "LIGHT_BLUE_CONCRETE").toUpperCase());
        if (material == null) material = Material.LIGHT_BLUE_CONCRETE;
        Location location = currentLocation();
        if (location == null) return;
        for (int i = -2; i <= 2; i++) {
            Location part = location.clone().add(direction.clone().multiply(i * 1.2));
            ArmorStand stand = (ArmorStand) world.spawnEntity(part, EntityType.ARMOR_STAND);
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setMarker(true);
            stand.setSilent(true);
            stand.setPersistent(false);
            if (stand.getEquipment() != null) {
                stand.getEquipment().setHelmet(new ItemStack(material));
            }
            body.add(stand);
        }
    }

    public void board(Player player) {
        Location location = currentLocation();
        if (location == null) return;
        passengers.add(player.getUniqueId());
        player.setGravity(false);
        player.setFallDistance(0);
        player.teleport(location.clone().add(0, 1.2, 0));
    }

    /** Chamado a cada tick pela partida. */
    public boolean tick() {
        if (!running) return false;
        travelled += speed;
        Location location = currentLocation();
        if (location == null) {
            running = false;
            return false;
        }

        for (int i = 0; i < body.size(); i++) {
            ArmorStand stand = body.get(i);
            if (!stand.isValid()) continue;
            Location part = location.clone().add(direction.clone().multiply((i - 2) * 1.2));
            stand.teleport(part);
        }
        if (plugin.configs().config().getBoolean("zone.particles", true)) {
            location.getWorld().spawnParticle(Particle.CLOUD, location.clone().subtract(0, 1, 0), 4, 0.6, 0.2, 0.6,
                    0.01);
        }

        for (UUID uuid : new ArrayList<>(passengers)) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                passengers.remove(uuid);
                continue;
            }
            Location target = location.clone().add(direction.clone().multiply(offsetFor(uuid)));
            target.add(0, 1.2, 0);
            target.setYaw(player.getLocation().getYaw());
            target.setPitch(player.getLocation().getPitch());
            player.setFallDistance(0);
            if (player.getLocation().distanceSquared(target) > 0.01) {
                player.teleport(target);
            }
            plugin.messages().actionBar(player, plugin.messages().raw("match.jump-hint"));
        }

        return travelled < length;
    }

    private double offsetFor(UUID uuid) {
        int hash = Math.abs(uuid.hashCode() % 5);
        return (hash - 2) * 1.2;
    }

    public void eject(Player player) {
        passengers.remove(player.getUniqueId());
        player.setGravity(true);
    }

    public void stop() {
        running = false;
        for (ArmorStand stand : body) {
            if (stand.isValid()) stand.remove();
        }
        body.clear();
        for (UUID uuid : new ArrayList<>(passengers)) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) player.setGravity(true);
        }
        passengers.clear();
    }
}
