package com.smp.br.player;

import com.smp.br.BattleRoyalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Guarda e restaura o estado do jogador no SMP.
 * Os dados sao gravados no disco IMEDIATAMENTE ao entrar, e so sao apagados
 * depois de uma restauracao bem sucedida - o que garante recuperacao total
 * apos reinicio do servidor ou reload do plugin.
 */
public class PlayerDataManager {

    private final BattleRoyalePlugin plugin;
    private final File folder;
    private final Map<UUID, PlayerSnapshot> pending = new HashMap<>();

    public PlayerDataManager(BattleRoyalePlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().severe("Nao foi possivel criar a pasta playerdata!");
        }
    }

    /** Carrega snapshots pendentes deixados por um crash/reinicio. */
    public void loadPending() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            PlayerSnapshot snapshot = PlayerSnapshot.fromYaml(yaml);
            if (snapshot == null) {
                plugin.getLogger().warning("Snapshot corrompido ignorado: " + file.getName());
                continue;
            }
            pending.put(snapshot.uuid(), snapshot);
        }
        if (!pending.isEmpty()) {
            plugin.getLogger().warning("Recuperando " + pending.size() + " inventario(s) pendente(s) do Battle Royale.");
        }
    }

    public boolean hasPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public PlayerSnapshot get(UUID uuid) {
        return pending.get(uuid);
    }

    public List<UUID> pendingIds() {
        return new ArrayList<>(pending.keySet());
    }

    /**
     * Salva o estado do jogador e limpa-o para a partida.
     * Retorna false se algo impedir a gravacao segura (o jogador nao deve entrar).
     */
    public boolean save(Player player) {
        UUID uuid = player.getUniqueId();
        if (pending.containsKey(uuid)) {
            // ja existe um snapshot: nunca sobrescrever (evita duplicacao de itens)
            plugin.getLogger().warning("Snapshot ja existente para " + player.getName() + " - entrada bloqueada.");
            return false;
        }
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        File file = fileOf(uuid);
        try {
            snapshot.toYaml().save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Falha ao salvar inventario de " + player.getName() + ": " + ex.getMessage());
            return false;
        }
        pending.put(uuid, snapshot);
        return true;
    }

    /** Prepara o jogador para o Battle Royale (inventario temporario limpo). */
    public void prepareForMatch(Player player) {
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        for (var effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) attribute.setBaseValue(20);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(10);
        player.setExhaustion(0);
        player.setLevel(0);
        player.setExp(0);
        player.setTotalExperience(0);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setGlowing(false);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.updateInventory();
    }

    /**
     * Restaura o jogador. Se estiver offline, o snapshot permanece pendente
     * e sera aplicado assim que ele entrar no servidor.
     */
    public boolean restore(UUID uuid) {
        PlayerSnapshot snapshot = pending.get(uuid);
        if (snapshot == null) return false;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return false;
        }
        try {
            snapshot.restore(player);
        } catch (Exception ex) {
            plugin.getLogger().severe("Falha ao restaurar " + player.getName() + ": " + ex.getMessage());
            return false;
        }
        discard(uuid);
        return true;
    }

    /** Remove o snapshot apenas apos restauracao confirmada. */
    private void discard(UUID uuid) {
        pending.remove(uuid);
        File file = fileOf(uuid);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Nao foi possivel apagar o snapshot de " + uuid);
        }
    }

    /** Regrava todos os snapshots pendentes (autosave de seguranca). */
    public void flush() {
        for (Map.Entry<UUID, PlayerSnapshot> entry : pending.entrySet()) {
            File file = fileOf(entry.getKey());
            if (file.exists()) continue;
            try {
                entry.getValue().toYaml().save(file);
            } catch (IOException ignored) {
                // nao ha o que fazer aqui alem de manter o snapshot em memoria
            }
        }
    }

    private File fileOf(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }
}
