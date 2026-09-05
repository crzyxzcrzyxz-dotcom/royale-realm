package com.smp.br.listeners;

import com.smp.br.BattleRoyalePlugin;
import com.smp.br.game.GameState;
import com.smp.br.game.Match;
import com.smp.br.game.Participant;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public class GameListener implements Listener {

    private final BattleRoyalePlugin plugin;

    public GameListener(BattleRoyalePlugin plugin) {
        this.plugin = plugin;
    }

    private Match match() {
        return plugin.matches().current();
    }

    // ------------------------------------------------------------------
    // Conexao
    // ------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.playerData().hasPending(player.getUniqueId())) {
            Match match = match();
            boolean stillPlaying = match != null && match.contains(player.getUniqueId());
            if (!stillPlaying) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (plugin.playerData().restore(player.getUniqueId())) {
                        plugin.messages().send(player, "recovery.restored");
                    }
                }, 10L);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Match match = match();
        if (match == null) return;
        Player player = event.getPlayer();
        Participant participant = match.participant(player.getUniqueId());
        if (participant == null) return;
        if (match.running() && participant.alive()) {
            // desconectar durante a partida conta como eliminacao (anti-exploit)
            participant.alive(false);
            participant.placement(Math.max(1, match.aliveCount() + 1));
            plugin.messages().broadcast("elimination.broadcast", "%player%", player.getName(), "%by%", "",
                    "%left%", String.valueOf(match.aliveCount()));
        }
        player.getInventory().clear();
        match.removeAndRestore(player.getUniqueId());
        match.checkWin();
    }

    // ------------------------------------------------------------------
    // Comandos
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.matches().isParticipating(player.getUniqueId())) return;
        String bypass = plugin.configs().config().getString("commands.bypass-permission", "battleroyale.bypass");
        if (bypass != null && player.hasPermission(bypass)) return;
        String command = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        for (String allowed : plugin.configs().config().getStringList("commands.whitelist")) {
            if (allowed.equalsIgnoreCase(command)) return;
        }
        event.setCancelled(true);
        plugin.messages().send(player, "commands.blocked");
        plugin.messages().sound(player, "error");
    }

    // ------------------------------------------------------------------
    // Onibus / Elytra / pouso
    // ------------------------------------------------------------------

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Match match = match();
        if (match == null || match.state() != GameState.BUS) return;
        if (!event.isSneaking()) return;
        if (!plugin.configs().config().getBoolean("bus.jump-on-sneak", true)) return;
        if (match.bus().hasPassenger(event.getPlayer().getUniqueId())) {
            match.jump(event.getPlayer());
        }
    }

    /** Desmontar o assento do onibus (SHIFT) = saltar. */
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Match match = match();
        if (match == null || match.state() != GameState.BUS) return;
        if (!match.bus().hasPassenger(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> match.jump(player));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Match match = match();
        if (match == null) return;
        Player player = event.getPlayer();
        Participant participant = match.participant(player.getUniqueId());
        if (participant == null) return;

        if (participant.gliding() && !participant.landed() && player.isOnGround()) {
            match.land(player);
            return;
        }
        if (!participant.landed() || !participant.alive()) return;
        if (event.getTo().getBlockY() == event.getFrom().getBlockY()
                && event.getTo().getBlockX() == event.getFrom().getBlockX()
                && event.getTo().getBlockZ() == event.getFrom().getBlockZ()) {
            return;
        }
        Location below = player.getLocation().clone().subtract(0, 0.2, 0);
        if (below.getBlock().getType() == Material.SLIME_BLOCK) {
            plugin.specialItems().launch(player);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Match match = match();
        if (match == null) return;
        if (!match.isPlaying(event.getPlayer().getUniqueId())) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            if (!plugin.matches().isInsidePlayableArea(event.getTo())) {
                event.setCancelled(true);
                plugin.messages().send(event.getPlayer(), "border.warn");
            }
        }
    }

    // ------------------------------------------------------------------
    // Dano / morte
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Match match = match();
        if (match == null) return;
        Participant participant = match.participant(player.getUniqueId());
        if (participant == null) return;

        if (match.state() == GameState.COUNTDOWN || match.state() == GameState.BOARDING || match.state() == GameState.BUS
                || match.state() == GameState.GLIDING || participant.spectating()) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (match.consumeNoFallDamage(player.getUniqueId()) || participant.gliding() || !participant.landed()) {
                event.setCancelled(true);
                return;
            }
        }
        participant.addDamageTaken(event.getFinalDamage());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player direct) {
            attacker = direct;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null) return;

        boolean victimIn = plugin.matches().isParticipating(victim.getUniqueId());
        boolean attackerIn = plugin.matches().isParticipating(attacker.getUniqueId());
        if (victimIn != attackerIn) {
            // nunca misturar participantes com jogadores do SMP
            event.setCancelled(true);
            return;
        }
        if (!victimIn) return;

        Match match = match();
        if (match == null) return;
        if (!match.running() || match.state() == GameState.BUS || match.state() == GameState.GLIDING) {
            event.setCancelled(true);
            return;
        }
        Participant attackerParticipant = match.participant(attacker.getUniqueId());
        Participant victimParticipant = match.participant(victim.getUniqueId());
        if (attackerParticipant != null && victimParticipant != null
                && attackerParticipant.team() == victimParticipant.team()
                && !plugin.configs().config().getBoolean("teams.friendly-fire", false)) {
            event.setCancelled(true);
            return;
        }
        if (attackerParticipant != null) {
            attackerParticipant.addDamageDealt(event.getFinalDamage());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Match match = match();
        if (match == null || !match.contains(player.getUniqueId())) return;
        match.createDeathChest(player, new java.util.ArrayList<>(event.getDrops()), player.getLocation());
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepLevel(true);
        event.setKeepInventory(false);
        Player killer = player.getKiller();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isDead()) {
                player.spigot().respawn();
            }
            match.eliminate(player, killer);
        }, 1L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Match match = match();
        if (match == null) return;
        if (!match.contains(event.getPlayer().getUniqueId())) return;
        Location start = match.map().start();
        if (start != null) {
            event.setRespawnLocation(start);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Match match = match();
        if (match == null) return;
        Participant participant = match.participant(player.getUniqueId());
        if (participant == null) return;
        if (!participant.landed() || participant.spectating()) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Construcao / regeneracao
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Match match = match();
        Player player = event.getPlayer();
        if (match != null && match.contains(player.getUniqueId())) {
            if (!match.isPlaying(player.getUniqueId()) || !match.participant(player.getUniqueId()).landed()) {
                event.setCancelled(true);
                plugin.messages().send(player, "protection.build");
                return;
            }
            plugin.regeneration().record(event.getBlockReplacedState().getBlock());
            return;
        }
        if (isProtectedWorld(player) && !player.hasPermission("battleroyale.bypass")) {
            event.setCancelled(true);
            plugin.messages().send(player, "protection.build");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Match match = match();
        Player player = event.getPlayer();
        if (match != null && match.contains(player.getUniqueId())) {
            if (!match.isPlaying(player.getUniqueId()) || !match.participant(player.getUniqueId()).landed()) {
                event.setCancelled(true);
                plugin.messages().send(player, "protection.build");
                return;
            }
            plugin.regeneration().record(event.getBlock());
            return;
        }
        if (isProtectedWorld(player) && !player.hasPermission("battleroyale.bypass")) {
            event.setCancelled(true);
            plugin.messages().send(player, "protection.build");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Match match = match();
        Participant participant = match == null ? null : match.participant(event.getPlayer().getUniqueId());
        if (participant == null) return;
        if (!participant.alive() || !participant.landed()) {
            event.setCancelled(true);
            return;
        }
        plugin.regeneration().record(event.getBlockClicked().getRelative(event.getBlockFace()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Match match = match();
        Participant participant = match == null ? null : match.participant(event.getPlayer().getUniqueId());
        if (participant == null) return;
        if (!participant.alive() || !participant.landed()) {
            event.setCancelled(true);
            return;
        }
        plugin.regeneration().record(event.getBlockClicked());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        plugin.regeneration().recordAll(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        plugin.regeneration().recordAll(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        plugin.regeneration().record(event.getBlock());
    }

    // ---- alteracoes INDIRETAS: sem isto o mapa nunca volta 100% ao original ----

    /** Agua/lava correndo. */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        plugin.regeneration().record(event.getToBlock());
    }

    /** Fogo apagando, gelo derretendo, folha sumindo. */
    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        plugin.regeneration().record(event.getBlock());
    }

    /** Neve/gelo/obsidiana se formando. */
    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        plugin.regeneration().record(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        plugin.regeneration().record(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        plugin.regeneration().record(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        plugin.regeneration().record(event.getBlock());
    }

    /** Areia caindo, enderman, barcos, ovelha comendo grama... */
    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        plugin.regeneration().record(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        plugin.regeneration().recordAll(event.getBlocks());
        for (Block block : event.getBlocks()) {
            plugin.regeneration().record(block.getRelative(event.getDirection()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        plugin.regeneration().recordAll(event.getBlocks());
        for (Block block : event.getBlocks()) {
            plugin.regeneration().record(block.getRelative(event.getDirection()));
        }
    }



    private boolean isProtectedWorld(Player player) {
        Match match = match();
        if (match == null) return false;
        return match.map().world() != null && match.map().world().equals(player.getWorld());
    }

    // ------------------------------------------------------------------
    // Itens / inventarios
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Match match = match();
        if (match == null) return;
        Participant participant = match.participant(player.getUniqueId());
        if (participant == null) return;

        if (participant.spectating()) {
            event.setCancelled(true);
            return;
        }
        if (!participant.landed()) {
            event.setCancelled(true);
            return;
        }
        if (!event.getAction().isRightClick()) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        String id = plugin.specialItems().idOf(item);
        if (id == null) return;
        if (plugin.specialItems().use(player, item, id)) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        plugin.specialItems().onProjectileHit(event.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Match match = match();
        if (match == null) return;
        Participant participant = match.participant(event.getPlayer().getUniqueId());
        if (participant == null) return;
        if (participant.spectating() || !participant.landed()) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "protection.drop");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Match match = match();
        if (match == null) return;
        Participant participant = match.participant(player.getUniqueId());
        if (participant == null) {
            // jogadores do SMP nao podem pegar itens do evento
            if (Match.isTemporary(event.getItem().getItemStack())) {
                event.setCancelled(true);
            }
            return;
        }
        if (participant.spectating() || !participant.landed()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Match match = match();
        if (match == null) return;
        Participant participant = match.participant(player.getUniqueId());
        if (participant == null) return;
        if (participant.spectating() || !participant.landed()) {
            event.setCancelled(true);
            plugin.messages().send(player, participant.spectating() ? "spectator.blocked" : "protection.interact");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Match match = match();
        if (match == null) return;
        Participant participant = match.participant(player.getUniqueId());
        if (participant == null) return;
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean tempElytra = (current != null && current.getType() == Material.ELYTRA && Match.isTemporary(current))
                || (cursor != null && cursor.getType() == Material.ELYTRA && Match.isTemporary(cursor));
        if (tempElytra || event.getSlotType() == InventoryType.SlotType.ARMOR && participant.gliding()) {
            event.setCancelled(true);
            player.updateInventory();
            return;
        }
        if (participant.spectating() || !participant.landed()) {
            event.setCancelled(true);
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Match match = match();
        Participant participant = match == null ? null : match.participant(player.getUniqueId());
        if (participant == null || !participant.gliding()) return;
        if (event.getRawSlots().contains(6) || (event.getOldCursor().getType() == Material.ELYTRA
                && Match.isTemporary(event.getOldCursor()))) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }
}
