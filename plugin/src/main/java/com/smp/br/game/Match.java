package com.smp.br.game;

import com.smp.br.BattleRoyalePlugin;
import com.smp.br.bus.BusManager;
import com.smp.br.map.BRMap;
import com.smp.br.util.Keys;
import com.smp.br.util.Text;
import com.smp.br.zone.ZoneManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Uma partida de Battle Royale, com maquina de estados completa.
 */
public class Match {

    private final BattleRoyalePlugin plugin;
    private final BRMap map;
    private final Map<UUID, Participant> participants = new LinkedHashMap<>();
    private final Set<UUID> noFallDamage = new HashSet<>();

    private final BusManager bus;
    private final ZoneManager zone;

    private GameState state = GameState.WAITING;
    private int countdown;
    private int tickCounter;
    private int glideTicks;
    private int elapsedSeconds;
    private int endingSeconds;
    private int placementCounter;
    private UUID winner;
    private boolean lootReady;

    public Match(BattleRoyalePlugin plugin, BRMap map) {
        this.plugin = plugin;
        this.map = map;
        this.bus = new BusManager(plugin, map);
        this.zone = new ZoneManager(plugin, map);
    }

    // ------------------------------------------------------------------
    // Acesso
    // ------------------------------------------------------------------

    public GameState state() {
        return state;
    }

    public BRMap map() {
        return map;
    }

    public ZoneManager zone() {
        return zone;
    }

    public BusManager bus() {
        return bus;
    }

    public int countdown() {
        return countdown;
    }

    public int elapsedSeconds() {
        return elapsedSeconds;
    }

    public Map<UUID, Participant> participants() {
        return participants;
    }

    public Participant participant(UUID uuid) {
        return participants.get(uuid);
    }

    public boolean contains(UUID uuid) {
        return participants.containsKey(uuid);
    }

    public boolean isPlaying(UUID uuid) {
        Participant participant = participants.get(uuid);
        return participant != null && participant.alive();
    }

    public boolean isSpectator(UUID uuid) {
        Participant participant = participants.get(uuid);
        return participant != null && participant.spectating();
    }

    public List<Participant> alive() {
        return participants.values().stream().filter(Participant::alive).toList();
    }

    public boolean joinOpen() {
        return state == GameState.COUNTDOWN;
    }

    public boolean running() {
        return state == GameState.BUS || state == GameState.GLIDING || state == GameState.ACTIVE
                || state == GameState.STORM || state == GameState.FINAL;
    }

    public void markNoFallDamage(Player player) {
        noFallDamage.add(player.getUniqueId());
    }

    public boolean consumeNoFallDamage(Player player) {
        return noFallDamage.remove(player.getUniqueId());
    }


    // ------------------------------------------------------------------
    // Fase de entrada
    // ------------------------------------------------------------------

    public void beginCountdown() {
        state = GameState.COUNTDOWN;
        countdown = plugin.configs().config().getInt("match.join-seconds", 45);
        plugin.regeneration().startTracking();
        // varre e prepara os baus enquanto os jogadores entram
        plugin.regeneration().scanContainers(map, found -> {
            int filled = plugin.regeneration().fillLoot(map);
            lootReady = true;
            plugin.getLogger().info("Baus encontrados: " + found + " | loot gerado em " + filled + " baus.");
        });
        announce();
    }

    public void announce() {
        String prefix = plugin.messages().prefix();
        List<String> lines = List.of("announce.header", "announce.title", "announce.map", "announce.time",
                "announce.players");
        for (String path : lines) {
            String message = plugin.messages().raw(path,
                    "%time%", String.valueOf(countdown),
                    "%players%", String.valueOf(participants.size()),
                    "%min%", String.valueOf(plugin.configs().config().getInt("match.min-players", 2)),
                    "%max%", String.valueOf(plugin.configs().config().getInt("match.max-players", 60)),
                    "%map%", map.displayName());
            Component component = Text.comp(message);
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(component));
        }
        Component buttons = buildButtons();
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(buttons));
        Component footer = Text.comp(plugin.messages().raw("announce.footer"));
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(footer));
        plugin.messages().soundAll("announce");
        if (prefix == null) {
            plugin.getLogger().warning("prefix ausente em messages.yml");
        }
    }

    /** Dois botoes independentes: ENTRAR e SAIR. */
    public Component buildButtons() {
        Component join = Text.comp(plugin.messages().raw("announce.button-join"))
                .clickEvent(ClickEvent.runCommand("/br join"))
                .hoverEvent(HoverEvent.showText(Text.comp(plugin.messages().raw("announce.button-join-hover"))));
        Component leave = Text.comp(plugin.messages().raw("announce.button-leave"))
                .clickEvent(ClickEvent.runCommand("/br leave"))
                .hoverEvent(HoverEvent.showText(Text.comp(plugin.messages().raw("announce.button-leave-hover"))));
        return Text.comp(plugin.messages().raw("announce.buttons-prefix"))
                .append(join)
                .append(Text.comp(plugin.messages().raw("announce.button-separator")))
                .append(leave);
    }

    public void sendButtons(Player player) {
        player.sendMessage(buildButtons());
    }

    public boolean join(Player player) {
        if (!joinOpen()) {
            plugin.messages().send(player, "join.not-open");
            return false;
        }
        if (participants.containsKey(player.getUniqueId())) {
            plugin.messages().send(player, "join.already");
            return false;
        }
        int max = plugin.configs().config().getInt("match.max-players", 60);
        if (participants.size() >= max) {
            plugin.messages().send(player, "join.full");
            return false;
        }
        if (!plugin.playerData().save(player)) {
            plugin.messages().sendRaw(player, "&cNao foi possivel salvar seus itens com seguranca. Entrada cancelada.");
            return false;
        }
        plugin.playerData().prepareForMatch(player);
        participants.put(player.getUniqueId(), new Participant(player.getUniqueId(), player.getName()));
        if (plugin.configs().config().getBoolean("match.teleport-on-join", false)) {
            Location start = map.start();
            if (start != null) player.teleport(start);
        }
        plugin.messages().send(player, "join.success");
        plugin.messages().sound(player, "join");
        if (plugin.configs().config().getBoolean("effects.join", true)) {
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 25, 0.5, 1, 0.5,
                    0.05);
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 15, 0.3, 1, 0.3, 0.02);
        }
        sendButtons(player);
        plugin.messages().broadcast("join.broadcast", "%player%", player.getName(), "%players%",
                String.valueOf(participants.size()), "%max%",
                String.valueOf(plugin.configs().config().getInt("match.max-players", 60)));
        return true;
    }

    /** Saida voluntaria (apenas durante a fase de entrada) ou saida de espectador. */
    public boolean leave(Player player) {
        Participant participant = participants.get(player.getUniqueId());
        if (participant == null) {
            plugin.messages().send(player, "leave.not-in");
            return false;
        }
        if (running() && participant.alive()) {
            plugin.messages().send(player, "leave.locked");
            return false;
        }
        removeAndRestore(player.getUniqueId());
        plugin.messages().send(player, "leave.success");
        plugin.messages().sound(player, "leave");
        if (plugin.configs().config().getBoolean("effects.leave", true)) {
            player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0.02);
        }
        if (state == GameState.COUNTDOWN) {
            plugin.messages().broadcast("leave.broadcast", "%player%", player.getName(), "%players%",
                    String.valueOf(participants.size()), "%max%",
                    String.valueOf(plugin.configs().config().getInt("match.max-players", 60)));
        }
        return true;
    }

    /** Remove o jogador da partida e devolve exatamente o estado original do SMP. */
    public void removeAndRestore(UUID uuid) {
        Participant participant = participants.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        noFallDamage.remove(uuid);
        if (player != null) {
            bus.eject(player);
            zone.detach(player);
            player.setGravity(true);
            player.setGliding(false);
            player.setFallDistance(0);
            plugin.playerData().restore(uuid);
        }
        if (participant != null && participant.spectating()) {
            participant.spectating(false);
        }
    }

    // ------------------------------------------------------------------
    // Loop principal
    // ------------------------------------------------------------------

    public void tick() {
        tickCounter++;
        boolean secondTick = tickCounter % 20 == 0;


        switch (state) {
            case COUNTDOWN -> {
                if (secondTick) tickCountdown();
                holdParticipants();
            }
            case BUS -> {
                if (!bus.tick()) {
                    forceEjectAll();
                }
            }
            case GLIDING -> {
                glideTicks++;
                if (allLanded()) {
                    beginActive();
                } else if (glideTicks > plugin.configs().config().getInt("match.glide-timeout-seconds", 60) * 20) {
                    forceLandAll();
                    beginActive();
                }
            }
            case ACTIVE, STORM, FINAL -> {
                if (secondTick) tickGame();
            }
            case ENDING -> {
                if (secondTick) {
                    endingSeconds--;
                    if (endingSeconds <= 0) {
                        finish();
                    }
                }
            }
            default -> {
                // WAITING / REGENERATING nao precisam de logica por tick
            }
        }
    }

    private void tickCountdown() {
        countdown--;
        List<Integer> announceAt = plugin.configs().config().getIntegerList("match.announce-at");
        if (announceAt.contains(countdown) && countdown > 0) {
            announce();
        }
        int titleSeconds = plugin.configs().config().getInt("match.countdown-title-seconds", 5);
        List<Integer> buttonsAt = plugin.configs().config().getIntegerList("match.buttons-at");
        if (buttonsAt.contains(countdown) && countdown > 0) {
            Component buttons = buildButtons();
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(buttons));
        }
        if (countdown <= titleSeconds && countdown > 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.messages().titleRaw(player,
                        plugin.messages().raw("match.countdown-title", "%time%", String.valueOf(countdown)),
                        plugin.messages().raw("match.countdown-subtitle"), 0, 25, 5);
                plugin.messages().sound(player, "countdown");
            }
        }
        for (Participant participant : participants.values()) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player != null) {
                plugin.messages().actionBar(player,
                        plugin.messages().raw("match.starting", "%time%", String.valueOf(Math.max(0, countdown))));
            }
        }
        int min = plugin.configs().config().getInt("match.min-players", 2);
        int board = Math.max(0, plugin.configs().config().getInt("bus.board-seconds", 5));
        if (countdown == board && participants.size() >= min && !bus.isRunning()) {
            startBus();
            return;
        }
        if (countdown <= 0) {
            if (participants.size() < min) {
                cancel();
            } else {
                if (!bus.isRunning()) startBus();
                // agora sim o onibus parte
                bus.depart();
                state = GameState.BUS;
                plugin.messages().broadcast("match.bus-start");
                plugin.messages().soundAll("bus-start");
            }
        }
    }

    /** Mantem os participantes no local configurado durante a fase de entrada. */
    private void holdParticipants() {
        if (tickCounter % 20 != 0) return;
        if (!plugin.configs().config().getBoolean("match.teleport-on-join", false)) return;
        Location start = map.start();
        if (start == null) return;
        for (Participant participant : participants.values()) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player == null) continue;
            if (!player.getWorld().equals(start.getWorld())
                    || player.getLocation().distanceSquared(start) > 400) {
                player.teleport(start);
            }
        }
    }

    private void tickGame() {
        elapsedSeconds++;
        zone.tickSecond();
        state = zone.mode() == ZoneManager.Mode.FINAL ? GameState.FINAL
                : (zone.mode() == ZoneManager.Mode.SHRINKING ? GameState.STORM : GameState.ACTIVE);


        for (Participant participant : new ArrayList<>(participants.values())) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player == null) continue;
            if (participant.spectating()) continue;
            if (!participant.alive()) continue;
            checkMapBounds(player, participant);
            if (!participants.containsKey(participant.uuid())) continue;
            boolean landed = participant.landed();
            boolean onlyAfterLanding = plugin.configs().config()
                    .getBoolean("zone.damage-only-after-landing", true);
            int grace = plugin.configs().config().getInt("zone.grace-seconds", 10);
            boolean damageAllowed = elapsedSeconds > grace && (!onlyAfterLanding || landed);
            zone.applyStorm(player, damageAllowed);
            zone.showRing(player);
        }

        int max = plugin.configs().config().getInt("match.max-duration-seconds", 1800);
        if (max > 0 && elapsedSeconds >= max) {
            checkWin();
            return;
        }

        checkWin();
    }

    private void checkMapBounds(Player player, Participant participant) {
        double dx = player.getLocation().getX() - map.centerX();
        double dz = player.getLocation().getZ() - map.centerZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double warn = plugin.configs().config().getDouble("border.warn-distance", 20);
        if (distance > map.radius() - warn && distance <= map.radius()) {
            plugin.messages().send(player, "border.warn");
            return;
        }
        if (distance <= map.radius()) return;
        String action = plugin.configs().config().getString("border.action", "TELEPORT");
        if ("ELIMINATE".equalsIgnoreCase(action)) {
            plugin.messages().send(player, "border.eliminated");
            eliminate(player, null);
        } else {
            double scale = (map.radius() - 5) / distance;
            Location target = player.getLocation().clone();
            target.setX(map.centerX() + dx * scale);
            target.setZ(map.centerZ() + dz * scale);
            target.setY(Math.max(target.getWorld().getHighestBlockYAt(target) + 1, target.getY()));
            player.teleport(target);
            markNoFallDamage(player);

            plugin.messages().send(player, "border.teleported");
        }
    }

    // ------------------------------------------------------------------
    // Onibus / salto / pouso
    // ------------------------------------------------------------------

    private void startBus() {
        if (!lootReady) {
            plugin.regeneration().fillLoot(map);
            lootReady = true;
        }
        List<Player> players = new ArrayList<>();
        for (Participant participant : participants.values()) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player == null) continue;
            participant.inBus(true);
            player.setGameMode(GameMode.SURVIVAL);
            zone.attach(player);
            players.add(player);
        }
        zone.start();
        bus.start(players);
        plugin.messages().broadcast("match.boarding");
        for (Player player : players) {
            plugin.messages().titleRaw(player, plugin.messages().raw("match.begin-title"),
                    plugin.messages().raw("match.begin-subtitle"), 5, 40, 10);
        }
    }

    public void jump(Player player) {
        Participant participant = participants.get(player.getUniqueId());
        if (participant == null || !participant.inBus()) return;
        participant.inBus(false);
        participant.gliding(true);
        bus.eject(player);
        player.setGravity(true);
        double boost = plugin.configs().config().getDouble("elytra.jump-boost", 0.4);
        player.setVelocity(player.getLocation().getDirection().multiply(boost).setY(-0.2));
        giveElytra(player);
        markNoFallDamage(player);
        plugin.messages().send(player, "match.jumped");
        plugin.messages().sound(player, "jump");
        if (plugin.configs().config().getBoolean("effects.jump", true)) {
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 30, 0.6, 0.3, 0.6, 0.05);
            player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation(), 20, 0.4, 0.4, 0.4, 0.08);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !player.isOnGround()) {
                player.setGliding(true);
            }
        }, 5L);
    }

    private void giveElytra(Player player) {
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.comp("&b&lELYTRA DE SALTO"));
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(Keys.TEMP, PersistentDataType.BYTE, (byte) 1);
            elytra.setItemMeta(meta);
        }
        player.getInventory().setChestplate(elytra);
    }

    /** Primeiro contato com o chao apos o salto. */
    public void land(Player player) {
        Participant participant = participants.get(player.getUniqueId());
        if (participant == null || participant.landed() || !participant.gliding()) return;
        participant.gliding(false);
        participant.landed(true);
        player.setGliding(false);
        player.setFallDistance(0);
        removeTemporaryElytra(player);
        markNoFallDamage(player);

        giveKit(player);
        plugin.messages().send(player, "match.landed");
        plugin.messages().sound(player, "land");
        if (plugin.configs().config().getBoolean("effects.land", true)) {
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 25, 0.6, 0.1, 0.6, 0.03);
            player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 1);
        }
        int resistance = plugin.configs().config().getInt("effects.landing-resistance-seconds", 3);
        if (resistance > 0) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.RESISTANCE, resistance * 20, 1, false, false));
        }
        if (state == GameState.GLIDING && allLanded()) {
            beginActive();
        }
    }

    public void removeTemporaryElytra(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        if (isTemporary(chest)) {
            player.getInventory().setChestplate(null);
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && item.getType() == Material.ELYTRA && isTemporary(item)) {
                player.getInventory().setItem(slot, null);
            }
        }
        player.updateInventory();
    }

    public static boolean isTemporary(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(Keys.TEMP, PersistentDataType.BYTE);
    }

    private void giveKit(Player player) {
        if (!plugin.configs().config().getBoolean("kit.enabled", true)) return;
        for (String raw : plugin.configs().config().getStringList("kit.items")) {
            String[] parts = raw.split(":");
            Material material = Material.matchMaterial(parts[0].toUpperCase());
            if (material == null) continue;
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                    amount = 1;
                }
            }
            ItemStack item = new ItemStack(material, Math.max(1, amount));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(Keys.TEMP, PersistentDataType.BYTE, (byte) 1);
                item.setItemMeta(meta);
            }
            player.getInventory().addItem(item);
        }
    }

    private boolean allLanded() {
        for (Participant participant : participants.values()) {
            if (!participant.alive()) continue;
            if (!participant.landed()) return false;
        }
        return !participants.isEmpty();
    }

    private void forceEjectAll() {
        plugin.messages().broadcast("match.force-eject");
        for (UUID uuid : new ArrayList<>(bus.passengers())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) jump(player);
        }
        bus.stop();
        state = GameState.GLIDING;
        glideTicks = 0;
    }

    private void forceLandAll() {
        for (Participant participant : participants.values()) {
            if (participant.landed() || !participant.alive()) continue;
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player == null) continue;
            Location ground = player.getLocation().clone();
            ground.setY(player.getWorld().getHighestBlockYAt(ground) + 1);
            player.teleport(ground);
            land(player);
        }
    }

    private void beginActive() {
        if (state == GameState.ACTIVE) return;
        bus.stop();
        state = GameState.ACTIVE;
        elapsedSeconds = 0;
        checkWin();
    }

    // ------------------------------------------------------------------
    // Eliminacoes / vitoria
    // ------------------------------------------------------------------

    public void eliminate(Player player, Player killer) {
        Participant participant = participants.get(player.getUniqueId());
        if (participant == null || !participant.alive()) return;
        participant.alive(false);
        participant.placement(Math.max(1, aliveCount() + 1));
        placementCounter++;

        if (killer != null) {
            Participant killerParticipant = participants.get(killer.getUniqueId());
            if (killerParticipant != null) {
                killerParticipant.addKill(player.getName());
                plugin.messages().send(killer, "elimination.killer", "%kills%",
                        String.valueOf(killerParticipant.kills()));
                plugin.messages().sound(killer, "elimination");
            }
        }

        plugin.messages().send(player, "elimination.self", "%place%", String.valueOf(participant.placement()),
                "%kills%", String.valueOf(participant.kills()));
        plugin.messages().broadcast("elimination.broadcast",
                "%player%", player.getName(),
                "%by%", killer == null ? "" : plugin.messages().raw("elimination.by", "%killer%", killer.getName()),
                "%left%", String.valueOf(aliveCount()));

        player.getInventory().clear();
        player.setGliding(false);
        player.setFallDistance(0);
        if (plugin.configs().config().getBoolean("effects.elimination", true)) {
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5,
                    0.05);
            player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.2);
        }
        if (plugin.configs().config().getBoolean("effects.lightning-on-elimination", true)) {
            player.getWorld().strikeLightningEffect(player.getLocation());
        }
        plugin.messages().soundAll("elimination");

        boolean spectatorEnabled = plugin.configs().config().getBoolean("spectator.enabled", true);
        if (!spectatorEnabled) {
            removeAndRestore(player.getUniqueId());
        } else if (plugin.configs().config().getBoolean("spectator.ask-player", true)) {
            offerSpectate(player);
        } else {
            enableSpectator(player);
        }
        checkWin();
    }

    private void offerSpectate(Player player) {
        Component ask = Text.comp(plugin.messages().prefix() + plugin.messages().raw("spectator.ask"));
        Component watch = Text.comp(plugin.messages().raw("spectator.ask-watch"))
                .clickEvent(ClickEvent.runCommand("/br spectate"));
        Component quit = Text.comp(plugin.messages().raw("spectator.ask-leave"))
                .clickEvent(ClickEvent.runCommand("/br leave"));
        player.sendMessage(ask);
        player.sendMessage(watch.append(Component.text("   ")).append(quit));
        int seconds = plugin.configs().config().getInt("spectator.choice-seconds", 15);
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Participant participant = participants.get(uuid);
            if (participant != null && !participant.alive() && !participant.spectating()) {
                removeAndRestore(uuid);
            }
        }, seconds * 20L);
        // enquanto decide, fica em espectador para nao ficar preso morrendo
        player.setGameMode(GameMode.SPECTATOR);
    }

    public void enableSpectator(Player player) {
        Participant participant = participants.get(player.getUniqueId());
        if (participant == null) return;
        participant.spectating(true);
        player.setGameMode(GameMode.SPECTATOR);
        player.getInventory().clear();
        plugin.messages().send(player, "spectator.enabled");
    }

    public int aliveCount() {
        return (int) participants.values().stream().filter(Participant::alive).count();
    }

    private Participant bestPlayer() {
        return participants.values().stream().filter(Participant::alive)
                .max(Comparator.comparingInt(Participant::kills)).orElse(null);
    }

    public void checkWin() {
        if (!running()) return;
        int alive = aliveCount();
        if (alive > 1) return;
        Participant last = participants.values().stream().filter(Participant::alive).findFirst().orElse(null);
        end(last);
    }

    public void end(Participant last) {
        if (state == GameState.ENDING || state == GameState.REGENERATING) return;
        state = GameState.ENDING;
        endingSeconds = Math.max(1, plugin.configs().config().getInt("match.end-delay-seconds", 8));
        bus.stop();

        if (last != null) {
            last.placement(1);
            winner = last.uuid();
            Player player = Bukkit.getPlayer(last.uuid());
            plugin.messages().broadcast("victory.broadcast", "%player%", last.name(), "%kills%",
                    String.valueOf(last.kills()));
            plugin.messages().soundAll("victory");
            for (Player online : Bukkit.getOnlinePlayers()) {
                plugin.messages().titleRaw(online,
                        plugin.messages().raw("victory.title"),
                        plugin.messages().raw("victory.subtitle", "%player%", last.name(), "%kills%",
                                String.valueOf(last.kills())),
                        10, 80, 20);
            }
            if (player != null && plugin.configs().config().getBoolean("effects.victory-fireworks", true)) {
                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 120, 1, 1,
                        1, 0.4);
                player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1, 0), 80, 1, 1, 1, 0.2);
            }
        } else {
            plugin.messages().broadcast("victory.none");
        }

        for (Participant participant : participants.values()) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player == null) continue;
            plugin.messages().send(player, "victory.stats",
                    "%kills%", String.valueOf(participant.kills()),
                    "%dealt%", String.format("%.1f", participant.damageDealt()),
                    "%taken%", String.format("%.1f", participant.damageTaken()),
                    "%place%", String.valueOf(participant.placement() == 0 ? 1 : participant.placement()));
        }
    }

    public UUID winner() {
        return winner;
    }

    public void cancel() {
        plugin.messages().broadcast("match.cancelled");
        for (UUID uuid : new ArrayList<>(participants.keySet())) {
            removeAndRestore(uuid);
        }
        bus.stop();
        zone.stop();

        state = GameState.REGENERATING;
        plugin.regeneration().restore(map, () -> {
            plugin.regeneration().stopTracking();
            state = GameState.WAITING;
            plugin.matches().onMatchFinished(this);
        });
    }

    /** Encerramento normal: restaura jogadores e regenera o mapa. */
    public void finish() {
        for (UUID uuid : new ArrayList<>(participants.keySet())) {
            removeAndRestore(uuid);
        }
        participants.clear();
        bus.stop();
        zone.stop();


        boolean reset = plugin.configs().config().getBoolean("regeneration.enabled", true)
                && plugin.configs().config().getBoolean("regeneration.reset-on-end", true);
        if (!reset) {
            plugin.regeneration().stopTracking();
            state = GameState.WAITING;
            plugin.matches().onMatchFinished(this);
            return;
        }
        state = GameState.REGENERATING;
        plugin.getLogger().info("Regenerando o mapa " + map.id() + "...");
        plugin.regeneration().restore(map, () -> {
            plugin.regeneration().stopTracking();
            int filled = plugin.regeneration().fillLoot(map);
            plugin.getLogger().info("Mapa regenerado. Loot novo em " + filled + " baus.");
            state = GameState.WAITING;
            plugin.matches().onMatchFinished(this);
        });
    }

    /** Encerramento de emergencia (shutdown/reload): prioridade absoluta aos itens. */
    public void emergencyStop() {
        for (UUID uuid : new ArrayList<>(participants.keySet())) {
            removeAndRestore(uuid);
        }
        participants.clear();
        bus.stop();
        zone.stop();
        plugin.regeneration().stopTracking();
    }
}
