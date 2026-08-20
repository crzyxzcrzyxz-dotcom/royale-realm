package com.smp.br.bus;

import com.smp.br.BattleRoyalePlugin;
import com.smp.br.map.BRMap;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Battle Bus: casco + balao feitos com BlockDisplay (leves e suaves) e os
 * jogadores viajam MONTADOS em assentos invisiveis - a camera fica 100% livre.
 * O onibus fica PARADO durante o embarque e so parte quando a contagem zera.
 */
public class BusManager {

    private final BattleRoyalePlugin plugin;
    private final BRMap map;

    private final List<BlockDisplay> body = new ArrayList<>();
    private final List<Vector> bodyOffsets = new ArrayList<>();

    private final Map<UUID, ArmorStand> seats = new LinkedHashMap<>();
    private final Map<UUID, Vector> seatOffsets = new LinkedHashMap<>();

    private Vector start;
    private Vector direction;
    private Vector side;
    private double length;
    private double speed;
    private double travelled;
    private boolean running;
    private boolean moving;

    public BusManager(BattleRoyalePlugin plugin, BRMap map) {
        this.plugin = plugin;
        this.map = map;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isMoving() {
        return moving;
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

    private Material material(String path, Material def) {
        Material material = Material.matchMaterial(
                plugin.configs().config().getString(path, def.name()).toUpperCase());
        return material == null ? def : material;
    }

    // ------------------------------------------------------------------

    /** Cria o onibus PARADO no inicio da rota e embarca os jogadores. */
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
        if (speed <= 0) speed = 0.85;
        travelled = 0;
        running = true;
        moving = false;

        spawnBody(world);
        for (Player player : players) {
            board(player);
        }
    }

    /** O onibus comeca a andar. */
    public void depart() {
        moving = true;
    }

    private Location offsetLocation(Location base, Vector offset) {
        return base.clone()
                .add(direction.clone().multiply(offset.getX()))
                .add(side.clone().multiply(offset.getZ()))
                .add(0, offset.getY(), 0);
    }

    private void addPart(World world, Location base, Vector offset, Material material) {
        Location location = offsetLocation(base, offset);
        BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
            entity.setBlock(material.createBlockData());
            entity.setPersistent(false);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setTeleportDuration(1);
            entity.setViewRange(4.0f);
        });
        body.add(display);
        bodyOffsets.add(offset);
    }

    /** Casco com janelas, faixa decorativa, nariz arredondado e balao no topo. */
    private void spawnBody(World world) {
        Material hull = material("bus.block", Material.LIGHT_BLUE_CONCRETE);
        Material glass = material("bus.glass-block", Material.LIGHT_BLUE_STAINED_GLASS);
        Material trim = material("bus.trim-block", Material.WHITE_CONCRETE);
        Material balloonBlock = material("bus.balloon-block", Material.BLUE_WOOL);

        int busLength = Math.max(5, cfgInt("bus.length", 17));
        int busWidth = Math.max(3, cfgInt("bus.width", 7));
        int busHeight = Math.max(2, cfgInt("bus.height-blocks", 4));
        boolean roof = plugin.configs().config().getBoolean("bus.roof", true);

        Location base = currentLocation();
        if (base == null) return;

        double half = busLength / 2.0;
        double halfWidth = busWidth / 2.0;
        for (int f = 0; f < busLength; f++) {
            double forward = -half + f + 0.5;
            boolean nose = f == 0 || f == busLength - 1;
            for (int s = 0; s < busWidth; s++) {
                double lateral = -halfWidth + s + 0.5;
                boolean sideEdge = s == 0 || s == busWidth - 1;
                // cantos arredondados
                if (nose && sideEdge) continue;
                // piso
                addPart(world, base, new Vector(forward, -1.6, lateral), hull);
                boolean edge = sideEdge || nose;
                if (edge) {
                    for (int h = 0; h < busHeight; h++) {
                        Material part = hull;
                        if (h == busHeight - 2) part = glass;
                        else if (h == 0) part = trim;
                        addPart(world, base, new Vector(forward, -1.6 + h + 1, lateral), part);
                    }
                }
                if (roof) {
                    addPart(world, base, new Vector(forward, -1.6 + busHeight + 1, lateral),
                            nose || sideEdge ? trim : hull);
                }
            }
        }

        // balao (estilo battle bus)
        if (plugin.configs().config().getBoolean("bus.balloon", true)) {
            int r = Math.max(2, cfgInt("bus.balloon-radius", 5));
            double top = -1.6 + busHeight + 3 + r;
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        double dist = Math.sqrt(x * x + y * y * 0.8 + z * z);
                        if (dist > r || dist < r - 1) continue;
                        addPart(world, base, new Vector(x, top + y, z),
                                (x + y + z) % 3 == 0 ? trim : balloonBlock);
                    }
                }
            }
            // cordas do balao
            for (int y = 0; y < 3; y++) {
                addPart(world, base, new Vector(0, -1.6 + busHeight + 2 + y, 0), trim);
            }
        }
        plugin.getLogger().info("Battle Bus criado com " + body.size() + " pecas.");
    }

    public void board(Player player) {
        Location base = currentLocation();
        World world = map.world();
        if (base == null || world == null) return;

        int busLength = Math.max(5, cfgInt("bus.length", 17));
        int busWidth = Math.max(3, cfgInt("bus.width", 7));
        int index = seats.size();
        int columns = Math.max(1, busWidth - 2);
        int rows = Math.max(1, busLength - 2);
        double forward = -busLength / 2.0 + 1.5 + ((index / columns) % rows);
        double lateral = -((columns - 1) / 2.0) + (index % columns);
        // O piso fica em -1.6; este offset deixa o assento dentro da cabine.
        Vector offset = new Vector(forward, -0.15, lateral);

        ArmorStand seat = (ArmorStand) world.spawnEntity(offsetLocation(base, offset), EntityType.ARMOR_STAND);
        seat.setVisible(false);
        seat.setGravity(false);
        seat.setInvulnerable(true);
        seat.setSilent(true);
        seat.setPersistent(false);
        seat.setBasePlate(false);
        seat.setMarker(true);
        seat.setCanTick(false);
        seats.put(player.getUniqueId(), seat);
        seatOffsets.put(player.getUniqueId(), offset);

        player.setGravity(false);
        player.setFallDistance(0);
        player.teleport(offsetLocation(base, offset));
        seat.addPassenger(player);
    }

    private void move(org.bukkit.entity.Entity entity, Location location) {
        if (entity == null || !entity.isValid()) return;
        entity.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN,
                TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    /** Chamado a cada tick pela partida. Retorna false quando a rota acabou. */
    public boolean tick() {
        if (!running) return false;
        if (moving) travelled += speed;
        Location location = currentLocation();
        if (location == null) {
            running = false;
            return false;
        }

        if (moving) {
            for (int i = 0; i < body.size(); i++) {
                move(body.get(i), offsetLocation(location, bodyOffsets.get(i)));
            }
            if (plugin.configs().config().getBoolean("effects.bus-trail", true)) {
                location.getWorld().spawnParticle(Particle.CLOUD, location.clone().subtract(0, 2.5, 0), 6, 1.2, 0.2,
                        1.2, 0.01);
                location.getWorld().spawnParticle(Particle.END_ROD, location.clone().subtract(0, 2.0, 0), 2, 1.5, 0.2,
                        1.5, 0.0);
            }
        }

        for (UUID uuid : new ArrayList<>(seats.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            ArmorStand seat = seats.get(uuid);
            if (player == null || !player.isOnline() || seat == null || !seat.isValid()) {
                eject(uuid);
                continue;
            }
            if (moving) move(seat, offsetLocation(location, seatOffsets.get(uuid)));
            player.setFallDistance(0);
            if (!seat.getPassengers().contains(player)) {
                seat.addPassenger(player);
            }
            plugin.messages().actionBar(player, plugin.messages().raw(
                    moving ? "match.jump-hint" : "match.boarding-hint"));
        }

        return !moving || travelled < length;
    }

    public void eject(Player player) {
        eject(player.getUniqueId());
        player.setGravity(true);
    }

    private void eject(UUID uuid) {
        ArmorStand seat = seats.remove(uuid);
        seatOffsets.remove(uuid);
        if (seat != null) {
            new ArrayList<>(seat.getPassengers()).forEach(seat::removePassenger);
            if (seat.isValid()) seat.remove();
        }
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) player.setGravity(true);
    }

    public void stop() {
        running = false;
        moving = false;
        for (BlockDisplay display : body) {
            if (display.isValid()) display.remove();
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
