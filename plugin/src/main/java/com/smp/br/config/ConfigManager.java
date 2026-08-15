package com.smp.br.config;

import com.smp.br.BattleRoyalePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ConfigManager {

    private final BattleRoyalePlugin plugin;

    private FileConfiguration config;
    private FileConfiguration maps;
    private FileConfiguration loot;
    private FileConfiguration items;
    private FileConfiguration messages;

    private File mapsFile;
    private File lootFile;
    private File itemsFile;
    private File messagesFile;

    public ConfigManager(BattleRoyalePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        mapsFile = prepare("maps.yml");
        lootFile = prepare("loot.yml");
        itemsFile = prepare("items.yml");
        messagesFile = prepare("messages.yml");

        maps = YamlConfiguration.loadConfiguration(mapsFile);
        loot = YamlConfiguration.loadConfiguration(lootFile);
        items = YamlConfiguration.loadConfiguration(itemsFile);
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private File prepare(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        return file;
    }

    public void saveMaps() {
        try {
            maps.save(mapsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Nao foi possivel salvar maps.yml: " + e.getMessage());
        }
    }

    public FileConfiguration config() {
        return config;
    }

    public FileConfiguration maps() {
        return maps;
    }

    public FileConfiguration loot() {
        return loot;
    }

    public FileConfiguration items() {
        return items;
    }

    public FileConfiguration messages() {
        return messages;
    }
}
