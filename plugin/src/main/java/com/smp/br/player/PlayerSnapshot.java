package com.smp.br.player;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot completo e restauravel do estado de um jogador no SMP.
 */
public class PlayerSnapshot {

    private final UUID uuid;
    private String name;
    private List<String> inventory = new ArrayList<>();
    private List<String> armor = new ArrayList<>();
    private List<String> enderChest = new ArrayList<>();
    private String offhand = "";
    private int heldSlot;
    private int level;
    private float exp;
    private int totalExperience;
    private double health = 20;
    private double maxHealth = 20;
    private int foodLevel = 20;
    private float saturation = 5;
    private float exhaustion;
    private List<PotionEffect> effects = new ArrayList<>();
    private String worldName = "";
    private double x, y, z;
    private float yaw, pitch;
    private String gameMode = "SURVIVAL";
    private boolean allowFlight;
    private boolean flying;
    private int fireTicks;
    private boolean glowing;

    private PlayerSnapshot(UUID uuid) {
        this.uuid = uuid;
    }

    public static PlayerSnapshot capture(Player player) {
        PlayerSnapshot snap = new PlayerSnapshot(player.getUniqueId());
        snap.name = player.getName();
        snap.inventory = encode(player.getInventory().getStorageContents());
        snap.armor = encode(player.getInventory().getArmorContents());
        snap.enderChest = encode(player.getEnderChest().getContents());
        snap.offhand = encodeOne(player.getInventory().getItemInOffHand());
        snap.heldSlot = player.getInventory().getHeldItemSlot();
        snap.level = player.getLevel();
        snap.exp = player.getExp();
        snap.totalExperience = player.getTotalExperience();
        snap.health = player.getHealth();
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        snap.maxHealth = attribute == null ? 20 : attribute.getBaseValue();
        snap.foodLevel = player.getFoodLevel();
        snap.saturation = player.getSaturation();
        snap.exhaustion = player.getExhaustion();
        snap.effects = new ArrayList<>(player.getActivePotionEffects());
        Location location = player.getLocation();
        snap.worldName = location.getWorld() == null ? "" : location.getWorld().getName();
        snap.x = location.getX();
        snap.y = location.getY();
        snap.z = location.getZ();
        snap.yaw = location.getYaw();
        snap.pitch = location.getPitch();
        snap.gameMode = player.getGameMode().name();
        snap.allowFlight = player.getAllowFlight();
        snap.flying = player.isFlying();
        snap.fireTicks = player.getFireTicks();
        snap.glowing = player.isGlowing();
        return snap;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public Location location() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (world == null) return null;
            return world.getSpawnLocation();
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Restaura completamente o jogador ao estado salvo.
     */
    public void restore(Player player) {
        player.closeInventory();
        for (PotionEffect active : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(active.getType());
        }
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(maxHealth);
        }
        player.setGameMode(parseGameMode());
        player.setFireTicks(Math.max(0, fireTicks));
        player.setGlowing(glowing);
        player.setFallDistance(0);

        player.getInventory().clear();
        player.getInventory().setStorageContents(decode(inventory, player.getInventory().getStorageContents().length));
        player.getInventory().setArmorContents(decode(armor, 4));
        player.getInventory().setItemInOffHand(decodeOne(offhand));
        player.getEnderChest().setContents(decode(enderChest, player.getEnderChest().getSize()));
        if (heldSlot >= 0 && heldSlot < 9) {
            player.getInventory().setHeldItemSlot(heldSlot);
        }

        player.setLevel(level);
        player.setExp(Math.max(0f, Math.min(1f, exp)));
        player.setTotalExperience(Math.max(0, totalExperience));
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setExhaustion(exhaustion);
        double safeHealth = Math.max(0.5, Math.min(health <= 0 ? maxHealth : health, maxHealth));
        player.setHealth(safeHealth);
        player.addPotionEffects(effects);
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && flying);

        Location target = location();
        if (target != null) {
            player.teleport(target);
        }
        player.updateInventory();
    }

    // ------------------------------------------------------------------
    // Serializacao
    // ------------------------------------------------------------------

    public YamlConfiguration toYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuid", uuid.toString());
        yaml.set("name", name);
        yaml.set("inventory", inventory);
        yaml.set("armor", armor);
        yaml.set("ender-chest", enderChest);
        yaml.set("offhand", offhand);
        yaml.set("held-slot", heldSlot);
        yaml.set("level", level);
        yaml.set("exp", exp);
        yaml.set("total-exp", totalExperience);
        yaml.set("health", health);
        yaml.set("max-health", maxHealth);
        yaml.set("food", foodLevel);
        yaml.set("saturation", saturation);
        yaml.set("exhaustion", exhaustion);
        yaml.set("effects", new ArrayList<>(effects));
        yaml.set("world", worldName);
        yaml.set("x", x);
        yaml.set("y", y);
        yaml.set("z", z);
        yaml.set("yaw", yaw);
        yaml.set("pitch", pitch);
        yaml.set("gamemode", gameMode);
        yaml.set("allow-flight", allowFlight);
        yaml.set("flying", flying);
        yaml.set("fire-ticks", fireTicks);
        yaml.set("glowing", glowing);
        return yaml;
    }

    @SuppressWarnings("unchecked")
    public static PlayerSnapshot fromYaml(ConfigurationSection yaml) {
        UUID uuid;
        try {
            uuid = UUID.fromString(yaml.getString("uuid", ""));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        PlayerSnapshot snap = new PlayerSnapshot(uuid);
        snap.name = yaml.getString("name", "");
        snap.inventory = new ArrayList<>(yaml.getStringList("inventory"));
        snap.armor = new ArrayList<>(yaml.getStringList("armor"));
        snap.enderChest = new ArrayList<>(yaml.getStringList("ender-chest"));
        snap.offhand = yaml.getString("offhand", "");
        snap.heldSlot = yaml.getInt("held-slot", 0);
        snap.level = yaml.getInt("level", 0);
        snap.exp = (float) yaml.getDouble("exp", 0);
        snap.totalExperience = yaml.getInt("total-exp", 0);
        snap.health = yaml.getDouble("health", 20);
        snap.maxHealth = yaml.getDouble("max-health", 20);
        snap.foodLevel = yaml.getInt("food", 20);
        snap.saturation = (float) yaml.getDouble("saturation", 5);
        snap.exhaustion = (float) yaml.getDouble("exhaustion", 0);
        List<?> rawEffects = yaml.getList("effects", new ArrayList<>());
        snap.effects = new ArrayList<>();
        for (Object entry : rawEffects) {
            if (entry instanceof PotionEffect effect) {
                snap.effects.add(effect);
            }
        }
        snap.worldName = yaml.getString("world", "");
        snap.x = yaml.getDouble("x");
        snap.y = yaml.getDouble("y");
        snap.z = yaml.getDouble("z");
        snap.yaw = (float) yaml.getDouble("yaw");
        snap.pitch = (float) yaml.getDouble("pitch");
        snap.gameMode = yaml.getString("gamemode", "SURVIVAL");
        snap.allowFlight = yaml.getBoolean("allow-flight", false);
        snap.flying = yaml.getBoolean("flying", false);
        snap.fireTicks = yaml.getInt("fire-ticks", 0);
        snap.glowing = yaml.getBoolean("glowing", false);
        return snap;
    }

    private GameMode parseGameMode() {
        try {
            return GameMode.valueOf(gameMode);
        } catch (IllegalArgumentException ex) {
            return GameMode.SURVIVAL;
        }
    }

    private static List<String> encode(ItemStack[] items) {
        List<String> encoded = new ArrayList<>();
        if (items == null) return encoded;
        for (ItemStack item : items) {
            encoded.add(encodeOne(item));
        }
        return encoded;
    }

    private static String encodeOne(ItemStack item) {
        if (item == null || item.getType().isAir()) return "";
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private static ItemStack[] decode(List<String> encoded, int size) {
        ItemStack[] items = new ItemStack[size];
        for (int i = 0; i < size && i < encoded.size(); i++) {
            items[i] = decodeOne(encoded.get(i));
        }
        return items;
    }

    private static ItemStack decodeOne(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[BattleRoyale] Falha ao desserializar item salvo: " + ex.getMessage());
            return null;
        }
    }
}
