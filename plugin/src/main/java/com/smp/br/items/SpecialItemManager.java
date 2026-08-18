package com.smp.br.items;

import com.smp.br.BattleRoyalePlugin;
import com.smp.br.util.Keys;
import com.smp.br.util.Text;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cria e executa todos os itens especiais.
 */
public class SpecialItemManager {

    private final BattleRoyalePlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> channeling = new HashMap<>();

    public SpecialItemManager(BattleRoyalePlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(String id) {
        ConfigurationSection section = plugin.configs().items().getConfigurationSection("specials." + id);
        if (section == null || !section.getBoolean("enabled", true)) return null;
        ItemStack item = new ItemStack(Material.valueOf(section.getString("material", "PAPER")));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.comp(section.getString("name", id)));
        meta.getPersistentDataContainer().set(Keys.SPECIAL, PersistentDataType.STRING, id);
        meta.getPersistentDataContainer().set(Keys.TEMP, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(Keys.SPECIAL, PersistentDataType.STRING);
    }

    public boolean use(Player player, ItemStack item, String id) {
        switch (id) {
            case "grappler" -> useGrappler(player, item);
            case "medkit" -> useMedkit(player, item);
            case "bandage" -> useBandage(player, item);
            case "shield-potion" -> useShield(player, item);
            case "jump-pad" -> throwProjectile(player, item, "jump-pad");
            case "smoke-bomb" -> throwProjectile(player, item, "smoke-bomb");
        }
        return true;
    }

    private void useGrappler(Player player, ItemStack item) {
        ConfigurationSection sec = plugin.configs().items().getConfigurationSection("grappler");
        RayTraceResult result = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getLocation().getDirection(), sec.getDouble("range", 30));
        if (result != null && result.getHitBlock() != null) {
            Vector v = result.getHitPosition().subtract(player.getLocation().toVector()).normalize().multiply(sec.getDouble("power", 1.5));
            v.setY(v.getY() + 0.5);
            player.setVelocity(v);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 1f, 1f);
            plugin.game().markNoFallDamage(player.getUniqueId());
        }
    }

    private void useMedkit(Player player, ItemStack item) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 1));
        player.setHealth(Math.min(player.getAttribute(Attribute.MAX_HEALTH).getValue(), player.getHealth() + 4));
        item.setAmount(item.getAmount() - 1);
    }

    private void useBandage(Player player, ItemStack item) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1));
        player.setHealth(Math.min(player.getAttribute(Attribute.MAX_HEALTH).getValue(), player.getHealth() + 2));
        item.setAmount(item.getAmount() - 1);
    }

    private void useShield(Player player, ItemStack item) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 1));
        item.setAmount(item.getAmount() - 1);
    }

    private void throwProjectile(Player player, ItemStack item, String id) {
        Projectile p = (id.equals("jump-pad") ? player.launchProjectile(Snowball.class) : player.launchProjectile(Snowball.class));
        p.getPersistentDataContainer().set(Keys.SPECIAL, PersistentDataType.STRING, id);
        item.setAmount(item.getAmount() - 1);
    }

    public void onProjectileHit(Entity p) {
        String id = p.getPersistentDataContainer().get(Keys.SPECIAL, PersistentDataType.STRING);
        if ("jump-pad".equals(id)) {
            Location loc = p.getLocation();
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                Location b = loc.clone().add(x, -1, z);
                b.getBlock().setType(Material.SLIME_BLOCK);
                plugin.regeneration().record(b.getBlock());
            }
        }
    }

    public void launch(Player player) {
        player.setVelocity(player.getLocation().getDirection().multiply(1.5).setY(1.5));
        plugin.game().markNoFallDamage(player.getUniqueId());
    }
}