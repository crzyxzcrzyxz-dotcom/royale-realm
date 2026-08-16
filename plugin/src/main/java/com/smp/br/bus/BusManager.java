package com.smp.br.bus;

import com.smp.br.BattleRoyalePlugin;
import com.smp.br.map.BRMap;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Battle Bus: estrutura grande de armor stands que percorre a rota configurada.
 * Os jogadores viajam MONTADOS em assentos invisiveis, portanto a camera fica
 * totalmente livre (nada de teleporte por tick travando o mouse).
 */
public class BusManager {

    private final BattleRoyalePlugin plugin;
    private final BRMap map;

    /** partes visuais: stand -> offset local (frente, lado, altura) */
    private final List<ArmorStand> body = new ArrayList<>();
    private final List<Vector> bodyOffsets = new ArrayList<>();

    /** assentos: jogador -> armor stand em que ele esta montado */
    private final Map<UUID, ArmorStand> seats = new LinkedHashMap<>();
    private final Map<UUID, Vector> seatOffsets = new LinkedHashMap<>();

    private Vector start;
    private Vector direction;
    private Vector side;
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
        return seats.containsKey(uuid);
    }

    public Set<UUID> passengers() {
        return seats.keySet();
    }

    public Location currentLocation() {
        World world = map.world();
        if (world == null || direction == null) return null;
        Vector position = start.clone().add(direction.clone().multiply(travelled));
        return new Location(world, position.getX(), map.busHeight(), position.getZ());
    }

    private int cfgInt(String path, int def) {
        return plugin.configs().config().getInt(path, def);
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
        side = new Vector(-direction.getZ(), 0, direction.getX());
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

    private Location offsetLocation(Location base, Vector offset) {
        return base.clone()
                .add(direction.clone().multiply(offset.getX()))
                .add(side.clone().multiply(offset.getZ()))
                .add(0, offset.getY(), 0);
    }

    private ArmorStand spawnStand(World world, Location location, Material helmet) {
        ArmorStand stand = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setPersistent(false);
        stand.setBasePlate(false);
        stand.setCanTick(false);
        if (helmet != null) {
            stand.setMarker(true);
            if (stand.getEquipment() != null) stand.getEquipment().setHelmet(new ItemStack(helmet));
        } else {
            stand.setMarker(false);
        }
        return stand;
    }

    /** Constroi um onibus grande: piso, laterais e teto (tamanho configuravel). */
    private void spawnBody(World world) {
        Material material = Material.matchMaterial(
                plugin.configs().config().getString("bus.block", "LIGHT_BLUE_CONCRETE").toUpperCase());
        if (material == null) material = Material.LIGHT_BLUE_CONCRETE;
        Material glass = Material.matchMaterial(
                plugin.configs().config().getString("bus.glass-block", "LIGHT_BLUE_STAINED_GLASS").toUpperCase());
        if (glass == null) glass = Material.LIGHT_BLUE_STAINED_GLASS;

        int busLength = Math.max(3, cfgInt("bus.length", 13));
        int busWidth = Math.max(1, cfgInt("bus.width", 5));
        int busHeight = Math.max(1, cfgInt("bus.height-blocks", 3));
        boolean roof = plugin.configs().config().getBoolean("bus.roof", true);

        Location base = currentLocation();
        if (base == null) return;

        double half = busLength / 2.0;
        double halfWidth = busWidth / 2.0;
        for (int f = 0; f < busLength; f++) {
            double forward = -half + f + 0.5;
            for (int s = 0; s < busWidth; s++) {
                double lateral = -halfWidth + s + 0.5;
                boolean edge = s == 0 || s == busWidth - 1 || f == 0 || f == busLength - 1;
                // piso completo
                addPart(world, base, new Vector(forward, -1.6, lateral), material);
                // paredes laterais / frente / tras
                if (edge) {
                    for (int h = 0; h < busHeight; h++) {
                        addPart(world, base, new Vector(forward, -1.6 + (h + 1), lateral),
                                h == busHeight - 1 ? glass : material);
                    }
                }
                // teto
                if (roof) {
                    addPart(world, base, new Vector(forward, -1.6 + busHeight + 1, lateral), material);
                }
            }
        }
    }

    private void addPart(World world, Location base, Vector offset, Material material) {
        Location location = offsetLocation(base, offset);
        ArmorStand stand = spawnStand(world, location, material);
        body.add(stand);
        bodyOffsets.add(offset);
    }

    public void board(Player player) {
        Location base = currentLocation();
        World world = map.world();
        if (base == null || world == null) return;

        int busLength = Math.max(3, cfgInt("bus.length", 13));
        int busWidth = Math.max(1, cfgInt("bus.width", 5));
        int index = seats.size();
        int columns = Math.max(1, busWidth - 2);
        double forward = -busLength / 2.0 + 1.5 + (index / columns) % Math.max(1, busLength - 2);
        double lateral = -((columns - 1) / 2.0) + (index % columns);
        Vector offset = new Vector(forward, -1.0, lateral);

        ArmorStand seat = spawnStand(world, offsetLocation(base, offset), null);
        seat.setGravity(false);
        seats.put(player.getUniqueId(), seat);
        seatOffsets.put(player.getUniqueId(), offset);

        player.setGravity(false);
        player.setFallDistance(0);
        player.teleport(offsetLocation(base, offset).add(0, 0.5, 0));
        seat.addPassenger(player);
    }

    private void move(ArmorStand stand, Location location) {
        if (stand == null || !stand.isValid()) return;
        stand.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN,
                TeleportFlag.EntityState.RETAIN_PASSENGERS);
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
            move(body.get(i), offsetLocation(location, bodyOffsets.get(i)));
        }

        boolean particles = plugin.configs().config().getBoolean("effects.bus-trail", true);
        if (particles) {
            location.getWorld().spawnParticle(Particle.CLOUD, location.clone().subtract(0, 2.5, 0), 6, 1.2, 0.2, 1.2,
                    0.01);
            location.getWorld().spawnParticle(Particle.END_ROD, location.clone().subtract(0, 2.0, 0), 2, 1.5, 0.2, 1.5,
                    0.0);
        }

        for (UUID uuid : new ArrayList<>(seats.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            ArmorStand seat = seats.get(uuid);
            if (player == null || !player.isOnline() || seat == null || !seat.isValid()) {
                eject(uuid);
                continue;
            }
            move(seat, offsetLocation(location, seatOffsets.get(uuid)));
            player.setFallDistance(0);
            if (!seat.getPassengers().contains(player)) {
                seat.addPassenger(player);
            }
            plugin.messages().actionBar(player, plugin.messages().raw("match.jump-hint"));
        }

        return travelled < length;
    }

    public void eject(Player player) {
        eject(player.getUniqueId());
        player.setGravity(true);
    }

    private void eject(UUID uuid) {
        ArmorStand seat = seats.remove(uuid);
        seatOffsets.remove(uuid);
        if (seat != null) {
            seat.getPassengers().forEach(seat::removePassenger);
            if (seat.isValid()) seat.remove();
        }
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) player.setGravity(true);
    }

    public void stop() {
        running = false;
        for (ArmorStand stand : body) {
            if (stand.isValid()) stand.remove();
        }
        body.clear();
        bodyOffsets.clear();
        for (UUID uuid : new ArrayList<>(seats.keySet())) {
            eject(uuid);
        }
        seats.clear();
        seatOffsets.clear();
    }
}
