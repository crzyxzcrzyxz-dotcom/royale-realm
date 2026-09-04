package com.smp.br.map;

import com.smp.br.BattleRoyalePlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class MapManager {

    private final BattleRoyalePlugin plugin;
    private final Map<String, BRMap> maps = new LinkedHashMap<>();
    private String forcedMap;

    public MapManager(BattleRoyalePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        maps.clear();
        ConfigurationSection root = plugin.configs().maps().getConfigurationSection("maps");
        if (root == null) {
            plugin.getLogger().warning("Nenhum mapa configurado em maps.yml");
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            maps.put(key.toLowerCase(), new BRMap(key.toLowerCase(), section));
        }
        plugin.getLogger().info("Mapas carregados: " + maps.size());
    }

    public Map<String, BRMap> maps() {
        return maps;
    }

    public BRMap get(String id) {
        return id == null ? null : maps.get(id.toLowerCase());
    }

    /**
     * Remove o mapa do plugin: apaga a entrada em maps.yml, tira da rotacao e
     * limpa o mapa forcado. NUNCA apaga a pasta/mundo do servidor.
     */
    public boolean remove(String id) {
        if (id == null) return false;
        String key = id.toLowerCase();
        if (!maps.containsKey(key)) return false;

        maps.remove(key);
        if (key.equals(forcedMap)) forcedMap = null;

        var config = plugin.configs().maps();
        config.set("maps." + key, null);
        List<String> order = new ArrayList<>(config.getStringList("rotation.order"));
        order.removeIf(entry -> entry != null && entry.equalsIgnoreCase(key));
        config.set("rotation.order", order);
        config.set("rotation.current", 0);
        plugin.configs().saveMaps();
        load();
        return true;
    }

    public List<String> rotationOrder() {
        List<String> order = new ArrayList<>(plugin.configs().maps().getStringList("rotation.order"));
        order.removeIf(id -> get(id) == null || !get(id).isReady());
        if (order.isEmpty()) {
            maps.values().stream().filter(BRMap::isReady).forEach(m -> order.add(m.id()));
        }
        return order;
    }

    public void forceMap(String id) {
        forcedMap = id == null ? null : id.toLowerCase();
    }

    public String forcedMap() {
        return forcedMap;
    }

    public BRMap current() {
        if (forcedMap != null) {
            BRMap map = get(forcedMap);
            if (map != null && map.isReady()) return map;
        }
        List<String> order = rotationOrder();
        if (order.isEmpty()) return null;
        int index = plugin.configs().maps().getInt("rotation.current", 0);
        if (index < 0 || index >= order.size()) index = 0;
        return get(order.get(index));
    }

    public BRMap next() {
        if (forcedMap != null) {
            BRMap map = get(forcedMap);
            forcedMap = null;
            if (map != null && map.isReady()) return map;
        }
        List<String> order = rotationOrder();
        if (order.isEmpty()) return null;
        String mode = plugin.configs().maps().getString("rotation.mode", "SEQUENTIAL");
        int index;
        if ("RANDOM".equalsIgnoreCase(mode)) {
            index = ThreadLocalRandom.current().nextInt(order.size());
        } else {
            index = plugin.configs().maps().getInt("rotation.current", 0);
            if (index < 0 || index >= order.size()) index = 0;
        }
        BRMap map = get(order.get(index));
        return map;
    }

    public void advance() {
        List<String> order = rotationOrder();
        if (order.isEmpty()) return;
        int index = plugin.configs().maps().getInt("rotation.current", 0) + 1;
        if (index >= order.size()) index = 0;
        plugin.configs().maps().set("rotation.current", index);
        plugin.configs().saveMaps();
    }
}
