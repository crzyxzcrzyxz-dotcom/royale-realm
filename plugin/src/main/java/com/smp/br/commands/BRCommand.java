package com.smp.br.commands;

import com.smp.br.BattleRoyalePlugin;
import com.smp.br.game.Match;
import com.smp.br.map.BRMap;
import com.smp.br.util.Text;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.text.Normalizer;

public class BRCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = Arrays.asList("ajuda", "entrar", "sair", "assistir", "botoes",
            "help", "join", "leave", "spectate", "start", "stop", "forcestart", "forceend", "reload", "status",
            "map", "setstart", "setbusstart", "setbusend", "setcenter", "setborder", "setzone", "setspawn",
            "regenerate", "reset", "loot", "debug", "criar", "create", "deletar", "delete", "canto1", "canto2",
            "corner1", "corner2");

    private final BattleRoyalePlugin plugin;

    public BRCommand(BattleRoyalePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join", "entrar" -> join(sender);
            case "leave", "sair" -> leave(sender);
            case "spectate", "assistir" -> spectate(sender);
            case "botoes", "buttons" -> buttons(sender);
            case "start", "iniciar" -> start(sender, args);
            case "stop", "forceend", "parar" -> stop(sender);
            case "forcestart" -> forceStart(sender);
            case "reload" -> reload(sender);
            case "status" -> status(sender);
            case "map" -> map(sender, args);
            case "setstart", "setspawn" -> setLocation(sender, "start");
            case "setbusstart" -> setBus(sender, "start");
            case "setbusend" -> setBus(sender, "end");
            case "setcenter" -> setCenter(sender);
            case "setborder" -> setBorder(sender, args);
            case "setzone" -> setZone(sender, args);
            case "regenerate", "reset" -> regenerate(sender);
            case "loot" -> loot(sender);
            case "debug" -> debug(sender);
            case "criar", "create" -> createMap(sender, args);
            case "deletar", "delete" -> deleteMap(sender, args);
            case "canto1", "corner1" -> setCorner(sender, "a");
            case "canto2", "corner2" -> setCorner(sender, "b");
            default -> help(sender);
        }
        return true;
    }

    // ------------------------------------------------------------------

    private void buttons(CommandSender sender) {
        Player player = player(sender);
        if (player == null) return;
        Match match = plugin.matches().current();
        if (match == null) {
            plugin.messages().send(player, "join.not-open");
            return;
        }
        match.sendButtons(player);
    }

    private void help(CommandSender sender) {
        plugin.messages().sendRaw(sender, "&8&m                                        ");
        plugin.messages().sendRaw(sender, "&b&l⚔ BATTLE ROYALE &7- comandos");
        plugin.messages().sendRaw(sender, "&7Jogadores:");
        plugin.messages().sendRaw(sender, " &f/br entrar &8(/br join) &7- entrar no evento");
        plugin.messages().sendRaw(sender, " &f/br sair &8(/br leave) &7- sair do evento");
        plugin.messages().sendRaw(sender, " &f/br assistir &7- assistir apos ser eliminado");
        plugin.messages().sendRaw(sender, " &f/br botoes &7- mostrar os botoes ENTRAR/SAIR no chat");
        if (sender.hasPermission("battleroyale.admin") || sender.hasPermission("battleroyale.start")) {
            plugin.messages().sendRaw(sender, "&7Administracao:");
            plugin.messages().sendRaw(sender, " &f/br start [mapa] &7- anunciar e iniciar um evento");
            plugin.messages().sendRaw(sender, " &f/br forcestart &7- pular a fase de entrada");
            plugin.messages().sendRaw(sender, " &f/br stop &7| &f/br forceend &7- encerrar a partida");
            plugin.messages().sendRaw(sender, " &f/br status &7- estado atual");
            plugin.messages().sendRaw(sender, " &f/br map list|next|set <mapa>");
            plugin.messages().sendRaw(sender, " &f/br setstart &7- define onde os jogadores aguardam");
            plugin.messages().sendRaw(sender, " &f/br setbusstart &7| &f/br setbusend &7- rota do onibus");
            plugin.messages().sendRaw(sender, " &f/br setcenter &7| &f/br setborder <raio>");
            plugin.messages().sendRaw(sender, " &f/br setzone <raio> &7- primeira safe zone na sua posicao");
            plugin.messages().sendRaw(sender, " &f/br criar <nome> &7- registra o mundo atual como mapa BR");
            plugin.messages().sendRaw(sender, " &f/br deletar <mapa> confirmar &7- remove o mapa do plugin");
            plugin.messages().sendRaw(sender, " &f/br canto1 &7| &f/br canto2 &7- define a borda do mapa por 2 pontos");
            plugin.messages().sendRaw(sender, " &f/br reset &8(/br regenerate) &7| &f/br loot &7| &f/br reload &7| &f/br debug");
        }
        plugin.messages().sendRaw(sender, "&8&m                                        ");
    }

    private void join(CommandSender sender) {
        Player player = player(sender);
        if (player == null) return;
        Match match = plugin.matches().current();
        if (match == null) {
            plugin.messages().send(player, "join.not-open");
            return;
        }
        match.join(player);
    }

    private void leave(CommandSender sender) {
        Player player = player(sender);
        if (player == null) return;
        Match match = plugin.matches().current();
        if (match == null) {
            plugin.messages().send(player, "leave.not-in");
            return;
        }
        match.leave(player);
    }

    private void spectate(CommandSender sender) {
        Player player = player(sender);
        if (player == null) return;
        if (!player.hasPermission("battleroyale.spectate")) {
            plugin.messages().send(player, "commands.no-permission");
            return;
        }
        Match match = plugin.matches().current();
        if (match == null || !match.contains(player.getUniqueId())) {
            plugin.messages().send(player, "match.no-match");
            return;
        }
        if (!plugin.configs().config().getBoolean("spectator.enabled", true)) {
            plugin.messages().send(player, "spectator.disabled");
            return;
        }
        match.enableSpectator(player);
    }

    private void start(CommandSender sender, String[] args) {
        if (!has(sender, "battleroyale.start")) return;
        String mapId = args.length > 1 ? args[1] : null;
        if (mapId != null && plugin.maps().get(mapId) == null) {
            plugin.messages().send(sender, "commands.map-unknown", "%map%", mapId);
            return;
        }
        if (!plugin.matches().start(mapId)) {
            plugin.messages().send(sender, "match.already-running");
        }
    }

    private void forceStart(CommandSender sender) {
        if (!has(sender, "battleroyale.start")) return;
        if (!plugin.matches().forceStart()) {
            plugin.messages().send(sender, "match.no-match");
        }
    }

    private void stop(CommandSender sender) {
        if (!has(sender, "battleroyale.stop")) return;
        if (plugin.matches().stop()) {
            plugin.messages().send(sender, "commands.stopped");
        } else {
            plugin.messages().send(sender, "match.no-match");
        }
    }

    private void reload(CommandSender sender) {
        if (!has(sender, "battleroyale.reload")) return;
        plugin.reloadAll();
        plugin.messages().send(sender, "commands.reloaded");
    }

    private void status(CommandSender sender) {
        if (!has(sender, "battleroyale.admin")) return;
        Match match = plugin.matches().current();
        BRMap map = plugin.maps().current();
        plugin.messages().sendRaw(sender, "&8&m                                        ");
        plugin.messages().sendRaw(sender, "&b&lSTATUS");
        plugin.messages().sendRaw(sender, "&7Estado: &f" + (match == null ? "WAITING" : match.state().name()));
        plugin.messages().sendRaw(sender, "&7Mapa atual: &f" + (map == null ? "nenhum" : map.displayName()));
        if (match != null) {
            plugin.messages().sendRaw(sender, "&7Participantes: &f" + match.participants().size()
                    + " &7| vivos: &f" + match.aliveCount());
            plugin.messages().sendRaw(sender, "&7Zona: &f" + (int) match.zone().radius() + " &7blocos | fase &f"
                    + (match.zone().phase() + 1) + "/" + match.zone().totalPhases());
            plugin.messages().sendRaw(sender, "&7Tempo decorrido: &f" + Text.time(match.elapsedSeconds()));
        }
        plugin.messages().sendRaw(sender, "&7Blocos rastreados: &f" + plugin.regeneration().trackedBlocks());
        plugin.messages().sendRaw(sender, "&8&m                                        ");
    }

    private void map(CommandSender sender, String[] args) {
        if (!has(sender, "battleroyale.admin")) return;
        if (args.length < 2) {
            plugin.messages().send(sender, "commands.usage", "%usage%", "/br map list|next|set <mapa>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                plugin.messages().sendRaw(sender, "&b&lMAPAS:");
                for (BRMap map : plugin.maps().maps().values()) {
                    plugin.messages().sendRaw(sender, " &7- &f" + map.id() + " &7(" + map.displayName() + "&7) "
                            + (map.isReady() ? "&aOK" : "&cmundo nao carregado"));
                }
            }
            case "next" -> {
                plugin.maps().advance();
                BRMap next = plugin.maps().current();
                plugin.messages().send(sender, "commands.map-set", "%map%", next == null ? "-" : next.id());
            }
            case "set" -> {
                if (args.length < 3) {
                    plugin.messages().send(sender, "commands.usage", "%usage%", "/br map set <mapa>");
                    return;
                }
                BRMap map = plugin.maps().get(args[2]);
                if (map == null) {
                    plugin.messages().send(sender, "commands.map-unknown", "%map%", args[2]);
                    return;
                }
                plugin.maps().forceMap(map.id());
                plugin.messages().send(sender, "commands.map-set", "%map%", map.id());
            }
            default -> plugin.messages().send(sender, "commands.usage", "%usage%", "/br map list|next|set <mapa>");
        }
    }

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    private ConfigurationSection targetSection(CommandSender sender) {
        BRMap map = plugin.maps().current();
        if (map == null) {
            plugin.messages().sendRaw(sender, "&cNenhum mapa configurado em maps.yml.");
            return null;
        }
        return map.section();
    }

    private void setLocation(CommandSender sender, String path) {
        if (!has(sender, "battleroyale.setup")) return;
        Player player = player(sender);
        if (player == null) return;
        ConfigurationSection section = targetSection(sender);
        if (section == null) return;
        Location location = player.getLocation();
        section.set("world", location.getWorld().getName());
        section.set(path + ".x", round(location.getX()));
        section.set(path + ".y", round(location.getY()));
        section.set(path + ".z", round(location.getZ()));
        section.set(path + ".yaw", round(location.getYaw()));
        section.set(path + ".pitch", round(location.getPitch()));
        plugin.configs().saveMaps();
        plugin.messages().send(sender, "commands.setup-saved", "%what%", path);
    }

    private void setBus(CommandSender sender, String which) {
        if (!has(sender, "battleroyale.setup")) return;
        Player player = player(sender);
        if (player == null) return;
        ConfigurationSection section = targetSection(sender);
        if (section == null) return;
        Location location = player.getLocation();
        section.set("bus." + which + ".x", round(location.getX()));
        section.set("bus." + which + ".z", round(location.getZ()));
        section.set("bus.height", round(Math.max(location.getY(), 120)));
        plugin.configs().saveMaps();
        plugin.messages().send(sender, "commands.setup-saved", "%what%", "bus." + which);
    }

    private void setCenter(CommandSender sender) {
        if (!has(sender, "battleroyale.setup")) return;
        Player player = player(sender);
        if (player == null) return;
        ConfigurationSection section = targetSection(sender);
        if (section == null) return;
        section.set("center.x", round(player.getLocation().getX()));
        section.set("center.z", round(player.getLocation().getZ()));
        plugin.configs().saveMaps();
        plugin.messages().send(sender, "commands.setup-saved", "%what%", "center");
    }

    private void setBorder(CommandSender sender, String[] args) {
        if (!has(sender, "battleroyale.setup")) return;
        ConfigurationSection section = targetSection(sender);
        if (section == null) return;
        if (args.length < 2) {
            plugin.messages().send(sender, "commands.usage", "%usage%", "/br setborder <raio>");
            return;
        }
        try {
            section.set("radius", Math.max(32, Integer.parseInt(args[1])));
        } catch (NumberFormatException ex) {
            plugin.messages().send(sender, "commands.usage", "%usage%", "/br setborder <raio>");
            return;
        }
        plugin.configs().saveMaps();
        plugin.messages().send(sender, "commands.setup-saved", "%what%", "radius");
    }

    private void setZone(CommandSender sender, String[] args) {
        if (!has(sender, "battleroyale.setup")) return;
        Player player = player(sender);
        if (player == null) return;
        ConfigurationSection section = targetSection(sender);
        if (section == null) return;
        int radius = 300;
        if (args.length > 1) {
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                radius = 300;
            }
        }
        section.set("zone.first.x", round(player.getLocation().getX()));
        section.set("zone.first.z", round(player.getLocation().getZ()));
        section.set("zone.first.radius", Math.max(16, radius));
        section.set("zone.initial.configured", true);
        plugin.configs().saveMaps();
        plugin.messages().send(sender, "commands.setup-saved", "%what%", "zone.first");
    }

    private void createMap(CommandSender sender, String[] args) {
        if (!has(sender, "battleroyale.setup")) return;
        Player player = player(sender);
        if (player == null) return;
        if (args.length < 2) {
            plugin.messages().send(sender, "commands.usage", "%usage%", "/br criar <nome com espacos>");
            return;
        }
        String displayName = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        String normalized = Normalizer.normalize(displayName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (normalized.isEmpty() || plugin.maps().get(normalized) != null) {
            plugin.messages().sendRaw(sender, "&cNome de mapa invalido ou ja existente.");
            return;
        }
        String root = "maps." + normalized;
        var maps = plugin.configs().maps();
        double radius = plugin.configs().config().getDouble("map-creation.default-radius", 400);
        double height = plugin.configs().config().getDouble("map-creation.default-bus-height", 170);
        maps.set(root + ".display-name", displayName);
        maps.set(root + ".world", player.getWorld().getName());
        maps.set(root + ".enabled", true);
        maps.set(root + ".start.x", player.getLocation().getX());
        maps.set(root + ".start.y", player.getLocation().getY());
        maps.set(root + ".start.z", player.getLocation().getZ());
        maps.set(root + ".center.x", 0.0);
        maps.set(root + ".center.z", 0.0);
        maps.set(root + ".radius", radius);
        maps.set(root + ".bus.start.x", -radius - 20);
        maps.set(root + ".bus.start.z", -radius - 20);
        maps.set(root + ".bus.end.x", radius + 20);
        maps.set(root + ".bus.end.z", radius + 20);
        maps.set(root + ".bus.height", height);
        maps.set(root + ".bus.speed", plugin.configs().config().getDouble("bus.default-speed", 0.85));
        maps.set(root + ".zone.initial.configured", false);
        maps.set(root + ".zone.first.x", 0.0);
        maps.set(root + ".zone.first.z", 0.0);
        maps.set(root + ".zone.first.radius", radius);
        maps.set(root + ".zone.final-radius", 8);
        maps.set(root + ".zone.phases", 6);
        maps.set(root + ".zone.wait-seconds", List.of(60, 50, 45, 40, 35, 30));
        maps.set(root + ".zone.shrink-seconds", List.of(60, 50, 45, 40, 35, 30));
        List<String> order = new ArrayList<>(maps.getStringList("rotation.order"));
        order.add(normalized);
        maps.set("rotation.order", order);
        plugin.configs().saveMaps();
        plugin.maps().load();
        plugin.maps().forceMap(normalized);
        plugin.messages().send(sender, "commands.map-created", "%map%", displayName,
                "%world%", player.getWorld().getName());
    }

    private void regenerate(CommandSender sender) {
        if (!has(sender, "battleroyale.admin")) return;
        BRMap map = plugin.maps().current();
        if (map == null || !map.isReady()) {
            plugin.messages().send(sender, "commands.map-unknown", "%map%", "-");
            return;
        }
        plugin.regeneration().restore(map, () -> plugin.messages().send(sender, "commands.regenerated"));
    }

    private void loot(CommandSender sender) {
        if (!has(sender, "battleroyale.admin")) return;
        BRMap map = plugin.maps().current();
        if (map == null || !map.isReady()) {
            plugin.messages().send(sender, "commands.map-unknown", "%map%", "-");
            return;
        }
        if (plugin.regeneration().knownContainers(map) == 0) {
            plugin.regeneration().scanContainers(map, found -> {
                int filled = plugin.regeneration().fillLoot(map);
                plugin.messages().send(sender, "commands.loot-filled", "%chests%", String.valueOf(filled));
            });
        } else {
            int filled = plugin.regeneration().fillLoot(map);
            plugin.messages().send(sender, "commands.loot-filled", "%chests%", String.valueOf(filled));
        }
    }

    private void debug(CommandSender sender) {
        if (!has(sender, "battleroyale.admin")) return;
        BRMap map = plugin.maps().current();
        plugin.messages().sendRaw(sender, "&7Mapas carregados: &f" + plugin.maps().maps().size());
        plugin.messages().sendRaw(sender, "&7Rotacao: &f" + plugin.maps().rotationOrder());
        plugin.messages().sendRaw(sender, "&7Snapshots pendentes: &f" + plugin.playerData().pendingIds().size());
        plugin.messages().sendRaw(sender, "&7Containers conhecidos: &f"
                + (map == null ? 0 : plugin.regeneration().knownContainers(map)));
        plugin.messages().sendRaw(sender, "&7Restaurando: &f" + plugin.regeneration().isRestoring()
                + " &7| rastreando: &f" + plugin.regeneration().isTracking());
    }

    // ------------------------------------------------------------------

    private boolean has(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("battleroyale.admin")) return true;
        plugin.messages().send(sender, "commands.no-permission");
        return false;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        plugin.messages().send(sender, "commands.players-only");
        return null;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUB) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) result.add(sub);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("map")) {
            result.addAll(List.of("list", "next", "set"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            result.addAll(plugin.maps().maps().keySet());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("map") && args[1].equalsIgnoreCase("set")) {
            result.addAll(plugin.maps().maps().keySet());
        }
        return result;
    }
}
