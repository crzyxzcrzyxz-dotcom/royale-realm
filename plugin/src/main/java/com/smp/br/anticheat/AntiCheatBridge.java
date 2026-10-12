package com.smp.br.anticheat;

import com.smp.br.BattleRoyalePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Isenta temporariamente o jogador de anti-cheats (GrimAC usa "grim.exempt")
 * durante movimentos controlados pelo plugin: onibus, camera aerea, planagem
 * e itens de impulso. Fora dessas janelas o anti-cheat volta a valer normalmente.
 */
public final class AntiCheatBridge {

    private final BattleRoyalePlugin plugin;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final Map<UUID, Long> expiry = new HashMap<>();
    private final Set<UUID> held = new HashSet<>();

    public AntiCheatBridge(BattleRoyalePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.configs().config().getBoolean("anticheat.exempt-enabled", true);
    }

    private List<String> permissions() {
        List<String> list = plugin.configs().config().getStringList("anticheat.exempt-permissions");
        return list.isEmpty() ? List.of("grim.exempt") : list;
    }

    private void attach(UUID uuid) {
        if (!enabled() || attachments.containsKey(uuid)) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        PermissionAttachment attachment = player.addAttachment(plugin);
        for (String permission : permissions()) attachment.setPermission(permission, true);
        attachments.put(uuid, attachment);
        player.recalculatePermissions();
    }

    /** Isencao sem prazo (ex.: enquanto estiver no onibus / planando). */
    public void hold(UUID uuid) {
        held.add(uuid);
        attach(uuid);
    }

    /** Termina a isencao sem prazo, mantendo uma folga de alguns segundos. */
    public void unhold(UUID uuid, int graceSeconds) {
        held.remove(uuid);
        exempt(uuid, graceSeconds);
    }

    /** Isencao temporaria (itens de impulso, launch pad, grappler...). */
    public void exempt(UUID uuid, int seconds) {
        if (seconds <= 0) return;
        long until = System.currentTimeMillis() + seconds * 1000L;
        expiry.merge(uuid, until, Math::max);
        attach(uuid);
    }

    /** Chamado 1x por segundo: remove isencoes vencidas, so com o jogador no chao. */
    public void sweep() {
        long now = System.currentTimeMillis();
        for (UUID uuid : Set.copyOf(attachments.keySet())) {
            if (held.contains(uuid)) continue;
            Long until = expiry.get(uuid);
            if (until != null && until > now) continue;
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && !player.isOnGround() && player.isOnline()) continue;
            clear(uuid);
        }
    }

    public void clear(UUID uuid) {
        held.remove(uuid);
        expiry.remove(uuid);
        PermissionAttachment attachment = attachments.remove(uuid);
        if (attachment == null) return;
        Player player = Bukkit.getPlayer(uuid);
        try {
            attachment.remove();
        } catch (IllegalArgumentException ignored) {
            // anexo ja invalidado (jogador saiu)
        }
        if (player != null) player.recalculatePermissions();
    }

    public void clearAll() {
        for (UUID uuid : Set.copyOf(attachments.keySet())) clear(uuid);
        held.clear();
        expiry.clear();
    }
}
