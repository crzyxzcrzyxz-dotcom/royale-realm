package com.smp.br.loot;

import com.smp.br.BattleRoyalePlugin;
import com.smp.br.util.Keys;
import com.smp.br.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gera o loot aleatorio dos baus com sistema de raridade.
 */
public class LootManager {

    private final BattleRoyalePlugin plugin;
    private final Map<Rarity, List<LootEntry>> tables = new EnumMap<>(Rarity.class);
    private final Map<Rarity, Double> chances = new EnumMap<>(Rarity.class);

    public LootManager(BattleRoyalePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        tables.clear();
        chances.clear();
        ConfigurationSection tablesSection = plugin.configs().loot().getConfigurationSection("tables");
        for (Rarity rarity : Rarity.values()) {
            List<LootEntry> entries = new ArrayList<>();
            if (tablesSection != null) {
                List<?> raw = tablesSection.getList(rarity.name());
                if (raw != null) {
                    for (Object object : raw) {
                        if (!(object instanceof Map<?, ?> map)) continue;
                        LootEntry entry = parse(map);
                        if (entry != null) entries.add(entry);
                    }
                }
            }
            tables.put(rarity, entries);
            chances.put(rarity, plugin.configs().config().getDouble("loot.chances." + rarity.name(), 10));
        }
        plugin.getLogger().info("Loot carregado: " + tables.values().stream().mapToInt(List::size).sum() + " entradas.");
    }

    private LootEntry parse(Map<?, ?> map) {
        Object material = map.get("material");
        if (material == null) return null;
        int min = number(map.get("min"), 1);
        int max = number(map.get("max"), min);
        double weight = map.get("weight") instanceof Number n ? n.doubleValue() : 1.0;
        List<String> enchants = new ArrayList<>();
        if (map.get("enchants") instanceof List<?> list) {
            for (Object entry : list) enchants.add(String.valueOf(entry));
        }
        String potion = map.get("potion") == null ? null : String.valueOf(map.get("potion"));
        return new LootEntry(String.valueOf(material), min, Math.max(min, max), Math.max(0.01, weight), enchants,
                potion);
    }

    private int number(Object object, int fallback) {
        return object instanceof Number n ? n.intValue() : fallback;
    }

    // ------------------------------------------------------------------
    // Preenchimento dos baus
    // ------------------------------------------------------------------

    public void fill(Inventory inventory, boolean hotZone) {
        inventory.clear();
        int min = plugin.configs().config().getInt("loot.min-slots", 3);
        int max = plugin.configs().config().getInt("loot.max-slots", 7);
        int size = inventory.getSize();
        int amount = min >= max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        amount = Math.min(amount, size);

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < size; i++) slots.add(i);
        java.util.Collections.shuffle(slots, ThreadLocalRandom.current());

        for (int i = 0; i < amount; i++) {
            Rarity rarity = rollRarity(hotZone);
            ItemStack item = roll(rarity);
            if (item == null) continue;
            inventory.setItem(slots.get(i), item);
        }
    }

    public Rarity rollRarity(boolean hotZone) {
        double multiplier = hotZone ? plugin.configs().config().getDouble("loot.hot-zone-multiplier", 2.0) : 1.0;
        Map<Rarity, Double> weights = new HashMap<>();
        double total = 0;
        for (Rarity rarity : Rarity.values()) {
            double weight = chances.getOrDefault(rarity, 0.0);
            if (rarity.ordinal() >= Rarity.RARE.ordinal()) {
                weight *= multiplier;
            }
            weights.put(rarity, weight);
            total += weight;
        }
        if (total <= 0) return Rarity.COMMON;
        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (Rarity rarity : Rarity.values()) {
            roll -= weights.getOrDefault(rarity, 0.0);
            if (roll <= 0) return rarity;
        }
        return Rarity.COMMON;
    }

    public ItemStack roll(Rarity rarity) {
        List<LootEntry> entries = tables.get(rarity);
        if (entries == null || entries.isEmpty()) {
            // fallback para a raridade abaixo
            for (int i = rarity.ordinal() - 1; i >= 0; i--) {
                List<LootEntry> fallback = tables.get(Rarity.values()[i]);
                if (fallback != null && !fallback.isEmpty()) {
                    entries = fallback;
                    rarity = Rarity.values()[i];
                    break;
                }
            }
        }
        if (entries == null || entries.isEmpty()) return null;

        double total = entries.stream().mapToDouble(LootEntry::weight).sum();
        double roll = ThreadLocalRandom.current().nextDouble(total);
        LootEntry chosen = entries.get(entries.size() - 1);
        for (LootEntry entry : entries) {
            roll -= entry.weight();
            if (roll <= 0) {
                chosen = entry;
                break;
            }
        }
        return build(chosen, rarity);
    }

    public ItemStack build(LootEntry entry, Rarity rarity) {
        ItemStack item;
        String materialName = entry.material();
        if (materialName.toUpperCase().startsWith("SPECIAL:")) {
            item = plugin.specialItems().create(materialName.substring(8).toLowerCase());
            if (item == null) return null;
            int amount = entry.min() >= entry.max() ? entry.min()
                    : ThreadLocalRandom.current().nextInt(entry.min(), entry.max() + 1);
            if (item.getMaxStackSize() > 1) item.setAmount(Math.max(1, amount));
            tag(item, rarity, false);
            return item;
        }
        Material material = Material.matchMaterial(materialName.toUpperCase());
        if (material == null) {
            plugin.getLogger().warning("Material invalido no loot.yml: " + materialName);
            return null;
        }
        int amount = entry.min() >= entry.max() ? entry.min()
                : ThreadLocalRandom.current().nextInt(entry.min(), entry.max() + 1);
        item = new ItemStack(material, Math.max(1, amount));

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            for (String enchantEntry : entry.enchants()) {
                applyEnchant(meta, enchantEntry, rarity);
            }
            applyRarityBonus(meta, material, rarity);
            if (entry.potion() != null && meta instanceof PotionMeta potionMeta) {
                try {
                    potionMeta.setBasePotionType(PotionType.valueOf(entry.potion().toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    // tipo de pocao invalido: mantem a pocao padrao
                }
            }
            item.setItemMeta(meta);
        }
        tag(item, rarity, true);
        return item;
    }

    private void applyEnchant(ItemMeta meta, String raw, Rarity rarity) {
        String[] parts = raw.split(":");
        if (parts.length == 0) return;
        Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(parts[0].toLowerCase()));
        if (enchantment == null) return;
        int level = 1;
        if (parts.length > 1) {
            try {
                level = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
                level = 1;
            }
        }
        // nunca ultrapassa o limite vanilla
        level = Math.max(1, Math.min(level, enchantment.getMaxLevel()));
        meta.addEnchant(enchantment, level, false);
    }

    private void applyRarityBonus(ItemMeta meta, Material material, Rarity rarity) {
        ConfigurationSection bonus = plugin.configs().loot().getConfigurationSection("bonus." + rarity.name());
        if (bonus == null || rarity == Rarity.COMMON) return;
        String name = material.name();
        int unbreaking = bonus.getInt("unbreaking", 0);
        int level = 0;
        String enchant = null;
        if (name.endsWith("_SWORD") || name.endsWith("_AXE")) {
            level = bonus.getInt("weapon", 0);
            enchant = "sharpness";
        } else if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) {
            level = bonus.getInt("armor", 0);
            enchant = "protection";
        } else if (material == Material.BOW) {
            level = bonus.getInt("bow", 0);
            enchant = "power";
        } else if (material == Material.CROSSBOW) {
            level = Math.min(3, bonus.getInt("bow", 0));
            enchant = "quick_charge";
        }
        if (enchant != null && level > 0) applyEnchant(meta, enchant + ":" + level, rarity);
        if (unbreaking > 0 && meta instanceof Damageable) {
            applyEnchant(meta, "unbreaking:" + unbreaking, rarity);
        }
    }

    /** Aplica nome com estrelas e cor da raridade + marca o item como temporario. */
    public void tag(ItemStack item, Rarity rarity, boolean rename) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        ConfigurationSection section = plugin.configs().loot().getConfigurationSection("rarities." + rarity.name());
        String color = section == null ? "&7" : section.getString("color", "&7");
        int stars = section == null ? 1 : section.getInt("stars", 1);
        if (rename) {
            String base = prettyName(item.getType());
            meta.displayName(Text.comp(color + "\u2605".repeat(Math.max(1, stars)) + " " + base));
        }
        meta.getPersistentDataContainer().set(Keys.RARITY, PersistentDataType.STRING, rarity.name());
        meta.getPersistentDataContainer().set(Keys.TEMP, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    public static String prettyName(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return builder.toString().trim();
    }
}
