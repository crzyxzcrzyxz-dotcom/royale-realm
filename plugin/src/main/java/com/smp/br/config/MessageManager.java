package com.smp.br.config;

import com.smp.br.BattleRoyalePlugin;
import com.smp.br.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;

public class MessageManager {

    private final BattleRoyalePlugin plugin;

    public MessageManager(BattleRoyalePlugin plugin) {
        this.plugin = plugin;
    }

    public String raw(String path, String... placeholders) {
        String value = plugin.configs().messages().getString(path, path);
        return Text.apply(value, placeholders);
    }

    public String prefix() {
        return plugin.configs().messages().getString("prefix", "");
    }

    public void send(CommandSender target, String path, String... placeholders) {
        String message = raw(path, placeholders);
        if (message.isEmpty()) return;
        target.sendMessage(Text.comp(prefix() + message));
    }

    public void sendRaw(CommandSender target, String message) {
        target.sendMessage(Text.comp(message));
    }

    public void broadcast(String path, String... placeholders) {
        String message = raw(path, placeholders);
        if (message.isEmpty()) return;
        Component component = Text.comp(prefix() + message);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(component));
    }

    public void broadcastRaw(String message) {
        Component component = Text.comp(prefix() + message);
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(component));
    }

    public void title(Player player, String titlePath, String subtitlePath, int fadeIn, int stay, int fadeOut,
                      String... placeholders) {
        Title title = Title.title(
                Text.comp(raw(titlePath, placeholders)),
                Text.comp(raw(subtitlePath, placeholders)),
                Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L),
                        Duration.ofMillis(fadeOut * 50L)));
        player.showTitle(title);
    }

    public void titleRaw(Player player, String titleText, String subtitleText, int fadeIn, int stay, int fadeOut) {
        player.showTitle(Title.title(Text.comp(titleText), Text.comp(subtitleText),
                Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L),
                        Duration.ofMillis(fadeOut * 50L))));
    }

    public void actionBar(Player player, String message) {
        player.sendActionBar(Text.comp(message));
    }

    public void sound(Player player, String key) {
        String sound = plugin.configs().config().getString("sounds." + key, "");
        if (sound == null || sound.isEmpty()) return;
        Location location = player.getLocation();
        player.playSound(location, sound, 1.0f, 1.0f);
    }

    public void soundAll(String key) {
        Bukkit.getOnlinePlayers().forEach(p -> sound(p, key));
    }
}
