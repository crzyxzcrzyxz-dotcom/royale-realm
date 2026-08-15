package com.smp.br.game;

import com.smp.br.BattleRoyalePlugin;
import com.smp.br.map.BRMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Controla o ciclo de vida das partidas: manual, automatico e recuperacao.
 */
public class MatchManager {

    private final BattleRoyalePlugin plugin;
    private Match current;
    private BukkitTask ticker;
    private int autoTimer;

    public MatchManager(BattleRoyalePlugin plugin) {
        this.plugin = plugin;
    }

    public Match current() {
        return current;
    }

    public boolean hasMatch() {
        return current != null && current.state() != GameState.WAITING;
    }

    public void startTicker() {
        if (ticker != null) return;
        autoTimer = plugin.configs().config().getInt("automatic.first-delay-seconds", 600);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
    }

    public void stopTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private int autoCounter;

    private void tick() {
        if (current != null) {
            current.tick();
        }
        autoCounter++;
        if (autoCounter % 20 != 0) return;
        tickAutomatic();
    }

    private void tickAutomatic() {
        if (!plugin.configs().config().getBoolean("automatic.enabled", true)) return;
        if (hasMatch()) return;
        if (autoTimer > 0) {
            autoTimer--;
            return;
        }
        int requiredOnline = plugin.configs().config().getInt("automatic.required-online", 2);
        if (Bukkit.getOnlinePlayers().size() < requiredOnline) {
            autoTimer = 30;
            return;
        }
        autoTimer = plugin.configs().config().getInt("automatic.interval-seconds", 1800);
        start(null);
    }

    /** Inicia um novo evento. Passe null para usar a rotacao de mapas. */
    public boolean start(String mapId) {
        if (hasMatch()) return false;
        BRMap map;
        if (mapId != null) {
            map = plugin.maps().get(mapId);
        } else {
            map = plugin.maps().next();
        }
        if (map == null || !map.isReady()) {
            plugin.getLogger().warning("Nenhum mapa valido disponivel (mundo carregado?).");
            return false;
        }
        current = new Match(plugin, map);
        current.beginCountdown();
        return true;
    }

    /** Forca o inicio imediato (encerra a contagem de entrada). */
    public boolean forceStart() {
        if (current == null || current.state() != GameState.COUNTDOWN) return false;
        while (current.countdown() > 1) {
            current.tick();
        }
        return true;
    }

    public boolean stop() {
        if (current == null) return false;
        current.end(null);
        current.finish();
        return true;
    }

    public void onMatchFinished(Match match) {
        if (current == match) {
            plugin.maps().advance();
            current = null;
        }
        autoTimer = plugin.configs().config().getInt("automatic.interval-seconds", 1800);
    }

    public void emergencyStop() {
        if (current != null) {
            current.emergencyStop();
            current = null;
        }
    }

    // ------------------------------------------------------------------
    // Helpers usados por listeners e itens
    // ------------------------------------------------------------------

    public boolean isParticipating(UUID uuid) {
        return current != null && current.contains(uuid);
    }

    public boolean isPlaying(UUID uuid) {
        return current != null && current.isPlaying(uuid);
    }

    public boolean isSpectator(UUID uuid) {
        return current != null && current.isSpectator(uuid);
    }

    public void markNoFallDamage(Player player) {
        if (current != null) current.markNoFallDamage(player.getUniqueId());
    }

    public boolean isInsidePlayableArea(Location location) {
        if (current == null) return true;
        BRMap map = current.map();
        if (map.world() == null || !map.world().equals(location.getWorld())) return false;
        double dx = location.getX() - map.centerX();
        double dz = location.getZ() - map.centerZ();
        return Math.sqrt(dx * dx + dz * dz) <= map.radius();
    }

    /** Recuperacao apos reinicio: devolve os itens de quem estava em partida. */
    public void recoverPending() {
        for (UUID uuid : plugin.playerData().pendingIds()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            if (plugin.playerData().restore(uuid)) {
                plugin.messages().send(player, "recovery.restored");
            }
        }
    }
}
