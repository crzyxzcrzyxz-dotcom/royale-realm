package com.smp.br;

import com.smp.br.commands.BRCommand;
import com.smp.br.config.ConfigManager;
import com.smp.br.config.MessageManager;
import com.smp.br.game.MatchManager;
import com.smp.br.items.SpecialItemManager;
import com.smp.br.listeners.GameListener;
import com.smp.br.loot.LootManager;
import com.smp.br.map.MapManager;
import com.smp.br.player.PlayerDataManager;
import com.smp.br.regen.RegenerationManager;
import com.smp.br.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class BattleRoyalePlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private PlayerDataManager playerDataManager;
    private MapManager mapManager;
    private LootManager lootManager;
    private SpecialItemManager specialItemManager;
    private RegenerationManager regenerationManager;
    private MatchManager matchManager;

    @Override
    public void onEnable() {
        Keys.init(this);

        configManager = new ConfigManager(this);
        configManager.load();

        messageManager = new MessageManager(this);
        playerDataManager = new PlayerDataManager(this);
        mapManager = new MapManager(this);
        lootManager = new LootManager(this);
        specialItemManager = new SpecialItemManager(this);
        regenerationManager = new RegenerationManager(this);
        matchManager = new MatchManager(this);

        mapManager.load();
        lootManager.load();

        // recuperacao apos reinicio/crash: inventarios NUNCA sao perdidos
        playerDataManager.loadPending();
        Bukkit.getScheduler().runTaskLater(this, () -> matchManager.recoverPending(), 40L);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        PluginCommand command = getCommand("br");
        if (command != null) {
            BRCommand executor = new BRCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        matchManager.startTicker();

        int autosave = Math.max(10, configManager.config().getInt("storage.autosave-seconds", 60));
        Bukkit.getScheduler().runTaskTimer(this, () -> playerDataManager.flush(), autosave * 20L, autosave * 20L);

        getLogger().info("BattleRoyale habilitado.");
    }

    @Override
    public void onDisable() {
        if (matchManager != null) {
            matchManager.stopTicker();
            matchManager.emergencyStop();
        }
        if (playerDataManager != null) {
            playerDataManager.flush();
        }
        Bukkit.getScheduler().cancelTasks(this);
        getLogger().info("BattleRoyale desabilitado com seguranca.");
    }

    public void reloadAll() {
        configManager.load();
        mapManager.load();
        lootManager.load();
    }

    public ConfigManager configs() {
        return configManager;
    }

    public MessageManager messages() {
        return messageManager;
    }

    public PlayerDataManager playerData() {
        return playerDataManager;
    }

    public MapManager maps() {
        return mapManager;
    }

    public LootManager loot() {
        return lootManager;
    }

    public SpecialItemManager specialItems() {
        return specialItemManager;
    }

    public RegenerationManager regeneration() {
        return regenerationManager;
    }

    public MatchManager matches() {
        return matchManager;
    }

    /** Alias semantico usado pelos itens especiais. */
    public MatchManager game() {
        return matchManager;
    }
}
