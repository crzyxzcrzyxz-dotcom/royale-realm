package com.smp.br.regen;

import com.smp.br.BattleRoyalePlugin;
import com.smp.br.map.BRMap;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registra e desfaz TODAS as alteracoes feitas no mapa durante uma partida.
 * Tambem cuida do snapshot e do reabastecimento dos baus existentes.
 */
public class RegenerationManager {

    private final BattleRoyalePlugin plugin;

    /** Estados originais dos blocos alterados durante a partida. */
    private final Map<String, BlockState> changed = new LinkedHashMap<>();

    /** Conteudo original dos containers do mapa (por mapa). */
    private final Map<String, Map<String, ItemStack[]>> containerSnapshots = new HashMap<>();

    /** Localizacao dos containers encontrados (por mapa). */
    private final Map<String, List<Location>> containers = new HashMap<>();

    private boolean tracking;
    private boolean restoring;

    public RegenerationManager(BattleRoyalePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isTracking() {
        return tracking;
    }

    public boolean isRestoring() {
        return restoring;
    }

    public void startTracking() {
        changed.clear();
        tracking = true;
    }

    public void stopTracking() {
        tracking = false;
    }

    public int trackedBlocks() {
        return changed.size();
    }

    public int knownContainers(BRMap map) {
        List<Location> list = containers.get(map.id());
        return list == null ? 0 : list.size();
    }

    public void record(Block block) {
        if (!tracking || block == null) return;
        String key = key(block.getLocation());
        if (changed.containsKey(key)) return;
        changed.put(key, block.getState());
    }

    public void recordAll(List<Block> blocks) {
        if (!tracking) return;
        for (Block block : blocks) record(block);
    }

    // ------------------------------------------------------------------
    // Baus
    // ------------------------------------------------------------------

    /**
     * Varre o mapa procurando containers. Executado em lotes para nao travar o servidor.
     * O callback recebe a quantidade encontrada.
     */
    public void scanContainers(BRMap map, java.util.function.IntConsumer callback) {
        World world = map.world();
        if (world == null) {
            callback.accept(0);
            return;
        }
        int radius = plugin.configs().config().getInt("regeneration.chest-scan-radius", 0);
        if (radius <= 0) radius = (int) Math.ceil(map.radius());
        int perTick = Math.max(1, plugin.configs().config().getInt("regeneration.chunks-per-tick", 12));
        final int scanRadius = radius;
        int centerChunkX = (int) map.centerX() >> 4;
        int centerChunkZ = (int) map.centerZ() >> 4;
        int chunkRadius = Math.max(1, radius >> 4);

        Deque<long[]> queue = new ArrayDeque<>();
        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                queue.add(new long[]{cx, cz});
            }
        }

        List<Location> found = new ArrayList<>();
        Map<String, ItemStack[]> snapshot = containerSnapshots.computeIfAbsent(map.id(), k -> new HashMap<>());
        boolean firstScan = snapshot.isEmpty();

        new BukkitRunnable() {
            @Override
            public void run() {
                int processed = 0;
                while (!queue.isEmpty() && processed < perTick) {
                    long[] coords = queue.poll();
                    processed++;
                    Chunk chunk = world.getChunkAt((int) coords[0], (int) coords[1]);
                    boolean wasLoaded = chunk.isLoaded();
                    if (!wasLoaded) chunk.load(false);
                    for (BlockState state : chunk.getTileEntities(false)) {
                        if (!(state instanceof Container container)) continue;
                        Location location = state.getLocation();
                        if (distanceXZ(location, map) > scanRadius) continue;
                        found.add(location);
                        if (firstScan) {
                            snapshot.put(key(location), copy(container.getInventory().getContents()));
                        }
                    }
                }
                if (queue.isEmpty()) {
                    containers.put(map.id(), found);
                    plugin.getLogger().info("Scan de baus concluido: " + found.size() + " container(es) no raio "
                            + scanRadius + ".");
                    cancel();
                    callback.accept(found.size());
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Estado "ao vivo" do container (evita escrever apenas em um snapshot). */
    private static Container liveContainer(Block block) {
        try {
            BlockState state = block.getState(false);
            if (state instanceof Container container) return container;
        } catch (Throwable ignored) {
            // API antiga: cai no snapshot
        }
        BlockState state = block.getState();
        return state instanceof Container container ? container : null;
    }

    /** Gera loot novo em todos os baus conhecidos do mapa. */
    public int fillLoot(BRMap map) {
        List<Location> list = containers.get(map.id());
        if (list == null || list.isEmpty()) return 0;
        double hotRadius = plugin.configs().config().getDouble("loot.hot-zone-radius", 25);
        List<Vector> hotZones = map.hotZones();
        int filled = 0;
        for (Location location : list) {
            Block block = location.getBlock();
            if (!block.getChunk().isLoaded()) block.getChunk().load(false);
            Container container = liveContainer(block);
            if (container == null) continue;
            boolean hot = false;
            for (Vector zone : hotZones) {
                double dx = zone.getX() - location.getX();
                double dz = zone.getZ() - location.getZ();
                if (Math.sqrt(dx * dx + dz * dz) <= hotRadius) {
                    hot = true;
                    break;
                }
            }
            Inventory inventory = container.getInventory();
            plugin.loot().fill(inventory, hot);
            container.update(true, false);
            filled++;
        }
        return filled;
    }

    /** Restaura o conteudo original de todos os containers do mapa. */
    public void restoreContainers(BRMap map) {
        Map<String, ItemStack[]> snapshot = containerSnapshots.get(map.id());
        List<Location> list = containers.get(map.id());
        if (snapshot == null || list == null) return;
        for (Location location : list) {
            Block block = location.getBlock();
            if (!block.getChunk().isLoaded()) block.getChunk().load(false);
            Container container = liveContainer(block);
            if (container == null) continue;
            ItemStack[] original = snapshot.get(key(location));
            container.getInventory().clear();
            if (original != null) {
                ItemStack[] contents = new ItemStack[container.getInventory().getSize()];
                System.arraycopy(original, 0, contents, 0, Math.min(original.length, contents.length));
                container.getInventory().setContents(contents);
            }
            container.update(true, false);
        }
    }

    public void invalidateSnapshots(BRMap map) {
        containerSnapshots.remove(map.id());
        containers.remove(map.id());
    }

    // ------------------------------------------------------------------
    // Restauracao dos blocos
    // ------------------------------------------------------------------

    /** Desfaz todas as alteracoes em lotes, sem travar o servidor. */
    /**
     * Restauracao em DUAS passagens:
     * 1) limpa (de cima para baixo) tudo que foi alterado - remove agua, fogo,
     *    blocos colocados e restos que impediam o mapa de voltar ao original;
     * 2) reaplica (de baixo para cima) o estado original de cada bloco.
     * Sem a primeira passagem, blocos apoiados/liquidos sobreviviam ao reset.
     */
    public void restore(BRMap map, Runnable done) {
        tracking = false;
        restoring = true;
        clearGroundItems(map);
        restoreContainers(map);

        int perTick = Math.max(50, plugin.configs().config().getInt("regeneration.blocks-per-tick", 400));
        List<BlockState> states = new ArrayList<>(changed.values());
        changed.clear();
        states.removeIf(java.util.Objects::isNull);

        List<BlockState> clearOrder = new ArrayList<>(states);
        clearOrder.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        List<BlockState> placeOrder = new ArrayList<>(states);
        placeOrder.sort(java.util.Comparator.comparingInt(BlockState::getY));

        Deque<BlockState> clearQueue = new ArrayDeque<>(clearOrder);
        Deque<BlockState> placeQueue = new ArrayDeque<>(placeOrder);
        int total = states.size();

        new BukkitRunnable() {
            @Override
            public void run() {
                int processed = 0;
                while (!clearQueue.isEmpty() && processed < perTick) {
                    BlockState state = clearQueue.pollFirst();
                    processed++;
                    try {
                        Block block = state.getBlock();
                        if (!block.getChunk().isLoaded()) block.getChunk().load();
                        if (block.getType() != state.getType()) {
                            block.setType(Material.AIR, false);
                        }
                    } catch (Exception ignored) {
                        // chunk indisponivel: a segunda passagem tenta de novo
                    }
                }
                while (clearQueue.isEmpty() && !placeQueue.isEmpty() && processed < perTick) {
                    BlockState state = placeQueue.pollFirst();
                    processed++;
                    try {
                        if (!state.getBlock().getChunk().isLoaded()) state.getBlock().getChunk().load();
                        state.update(true, false);
                    } catch (Exception ignored) {
                        // estado invalido
                    }
                }
                if (clearQueue.isEmpty() && placeQueue.isEmpty()) {
                    restoreContainers(map);
                    clearGroundItems(map);
                    restoring = false;
                    cancel();
                    plugin.getLogger().info("Mapa restaurado: " + total + " bloco(s) revertido(s).");
                    if (done != null) done.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Remove itens dropados e entidades criadas durante a partida. */
    public void clearGroundItems(BRMap map) {
        if (!plugin.configs().config().getBoolean("regeneration.clear-drops", true)) return;
        World world = map.world();
        if (world == null) return;
        double radius = map.radius() + 100;
        boolean clearEntities = plugin.configs().config().getBoolean("regeneration.clear-entities", true);
        for (Entity entity : world.getEntities()) {
            EntityType type = entity.getType();
            boolean transientEntity = type == EntityType.ITEM || type == EntityType.ARROW || type == EntityType.SPECTRAL_ARROW
                    || type == EntityType.EXPERIENCE_ORB || type == EntityType.SNOWBALL
                    || type == EntityType.FIREWORK_ROCKET || type == EntityType.SPLASH_POTION
                    || type == EntityType.TRIDENT || type == EntityType.EGG || type == EntityType.ENDER_PEARL
                    || type == EntityType.FIREBALL || type == EntityType.SMALL_FIREBALL || type == EntityType.TNT;
            if (!transientEntity && !(clearEntities && entity.getPersistentDataContainer()
                    .has(com.smp.br.util.Keys.TEMP, org.bukkit.persistence.PersistentDataType.BYTE))) {
                continue;
            }
            if (distanceXZ(entity.getLocation(), map) > radius) continue;
            entity.remove();
        }
    }

    private static double distanceXZ(Location location, BRMap map) {
        double dx = location.getX() - map.centerX();
        double dz = location.getZ() - map.centerZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static ItemStack[] copy(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private static String key(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":"
                + location.getBlockZ();
    }
}
