package com.smp.br.items;

import com.smp.br.BattleRoyalePlugin;
import com.smp.br.util.Keys;
import com.smp.br.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
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
 * Cria e executa todos os itens especiais (Grappler, Medkit, Launch Pad, etc).
 */
public class SpecialItemManager {

    private final BattleRoyalePlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> channeling = new HashMap<>();

    public SpecialItemManager(BattleRoyalePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Criacao
    // ------------------------------------------------------------------

    public ItemStack create(String id) {
        if (id == null) return null;
        if (id.equalsIgnoreCase("grappler")) return createGrappler();
        ConfigurationSection section = plugin.configs().items().getConfigurationSection("specials." + id);
        if (section == null || !section.getBoolean("enabled", true)) return null;
        Material material = material(section.getString("material", "PAPER"), Material.PAPER);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.comp(section.getString("name", id)));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String line : section.getStringList("lore")) {
                lore.add(Text.comp(line
                        .replace("%regen%", String.valueOf(section.getInt("regen-seconds", 0)))
                        .replace("%seconds%", String.valueOf(section.getInt("seconds", 0)))));
            }
            meta.lore(lore);
            meta.getPersistentDataContainer().set(Keys.SPECIAL, PersistentDataType.STRING, id.toLowerCase());
            meta.getPersistentDataContainer().set(Keys.TEMP, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createGrappler() {
        ConfigurationSection section = plugin.configs().items().getConfigurationSection("grappler");
        if (section == null || !section.getBoolean("enabled", true)) return null;
        int uses = section.getInt("uses", 12);
        ItemStack item = new ItemStack(material(section.getString("material", "FISHING_ROD"), Material.FISHING_ROD));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.comp(section.getString("name", "&b&lGRAPPLER")));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String line : section.getStringList("lore")) {
                lore.add(Text.comp(line.replace("%range%", String.valueOf(section.getInt("range", 28)))
                        .replace("%uses%", String.valueOf(uses))));
            }
            meta.lore(lore);
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(Keys.SPECIAL, PersistentDataType.STRING, "grappler");
            meta.getPersistentDataContainer().set(Keys.USES, PersistentDataType.INTEGER, uses);
            meta.getPersistentDataContainer().set(Keys.TEMP, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        item.setAmount(1);
        return item;
    }

    public String idOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(Keys.SPECIAL, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------
    // Uso
    // ------------------------------------------------------------------

    /** @return true se o evento deve ser cancelado. */
    public boolean use(Player player, ItemStack item, String id) {
        switch (id) {
            case "grappler" -> {
                return useGrappler(player, item);
            }
            case "medkit" -> {
                return useMedkit(player, item);
            }
            case "shield-potion" -> {
                return useShieldPotion(player, item);
            }
            case "big-shield" -> {
                return useShield(player, item, "big-shield");
            }
            case "chug-jug" -> {
                return useChugJug(player, item);
            }
            case "bandage" -> {
                return useBandage(player, item);
            }
            case "impulse" -> {
                return throwSpecial(player, item, "impulse", 1.8);
            }
            case "smoke-bomb" -> {
                return throwSmoke(player, item);
            }
            case "shockwave", "boogie-bomb", "launch-pad", "port-a-fort" -> {
                return throwSpecial(player, item, id, 1.65);
            }
            // Todos arremessaveis: ativam no impacto, sem precisar mirar num bloco.
            case "fireball", "freeze-grenade", "lightning-grenade", "healing-grenade", "cluster-bomb" -> {
                return throwSpecial(player, item, id, 1.9);
            }
            case "rift" -> {
                return useRift(player, item);
            }
            case "adrenaline" -> {
                return useAdrenaline(player, item);
            }
            default -> {
                return false;
            }
        }
    }

    private boolean useGrappler(Player player, ItemStack item) {
        ConfigurationSection section = plugin.configs().items().getConfigurationSection("grappler");
        if (section == null) return true;
        if (onCooldown(player, "grappler", section.getInt("cooldown-ticks", 30))) {
            plugin.messages().send(player, "special.grappler-cooldown");
            plugin.messages().sound(player, "error");
            return true;
        }
        double range = section.getDouble("range", 64);
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        RayTraceResult result = player.getWorld().rayTraceBlocks(eye, direction, range,
                FluidCollisionMode.NEVER, true);
        // Sem bloco na mira NAO e erro: usa o fim do alcance configurado.
        // (era isto que limitava o gancho ao alcance de interacao vanilla)
        Location target = (result != null && result.getHitBlock() != null)
                ? result.getHitPosition().toLocation(player.getWorld())
                : eye.clone().add(direction.clone().multiply(range));
        if (!plugin.matches().isInsidePlayableArea(target)) {
            plugin.messages().send(player, "special.grappler-no-target");
            plugin.messages().sound(player, "error");
            return true;
        }
        // linha visual
        Location eye = player.getEyeLocation();
        Vector step = target.toVector().subtract(eye.toVector());
        double distance = step.length();
        if (distance < 0.5) return true;
        Vector unit = step.clone().normalize().multiply(0.5);
        Location point = eye.clone();
        for (double travelled = 0; travelled < distance; travelled += 0.5) {
            point.add(unit);
            player.getWorld().spawnParticle(Particle.CRIT, point, 1, 0, 0, 0, 0);
        }

        double scaledPower = Math.min(section.getDouble("max-power", 3.2),
                section.getDouble("power", 1.35) + distance / Math.max(20.0, range) * 1.4);
        Vector pull = target.toVector().subtract(player.getLocation().toVector()).normalize().multiply(scaledPower);
        pull.setY(Math.max(pull.getY(), 0) + section.getDouble("vertical-bonus", 0.35));
        player.setVelocity(pull);
        player.setFallDistance(0);
        plugin.messages().sound(player, "grappler");
        plugin.messages().sound(player, "grappler-pull");
        plugin.game().markNoFallDamage(player);

        int uses = consumeUse(player, item);
        if (uses > 0) {
            plugin.messages().send(player, "special.grappler-uses", "%uses%", String.valueOf(uses));
        } else {
            plugin.messages().send(player, "special.grappler-empty");
        }
        setCooldown(player, "grappler", section.getInt("cooldown-ticks", 30));
        return true;
    }

    private int consumeUse(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer uses = meta.getPersistentDataContainer().get(Keys.USES, PersistentDataType.INTEGER);
        int left = (uses == null ? 1 : uses) - 1;
        if (left <= 0) {
            item.setAmount(0);
            return 0;
        }
        meta.getPersistentDataContainer().set(Keys.USES, PersistentDataType.INTEGER, left);
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        ConfigurationSection section = plugin.configs().items().getConfigurationSection("grappler");
        if (section != null) {
            for (String line : section.getStringList("lore")) {
                lore.add(Text.comp(line.replace("%range%", String.valueOf(section.getInt("range", 28)))
                        .replace("%uses%", String.valueOf(left))));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return left;
    }

    private boolean useMedkit(Player player, ItemStack item) {
        ConfigurationSection section = plugin.configs().items().getConfigurationSection("specials.medkit");
        if (section == null) return true;
        if (channeling.containsKey(player.getUniqueId())) return true;
        int ticks = section.getInt("channel-ticks", 60);
        double heal = section.getDouble("heal", 20.0);
        Location origin = player.getLocation().clone();
        plugin.messages().send(player, "special.medkit-start", "%time%", String.valueOf(ticks / 20));
        int taskId = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    channeling.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                if (player.getLocation().distanceSquared(origin) > 1.5) {
                    plugin.messages().send(player, "special.medkit-cancel");
                    channeling.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
                elapsed += 5;
                if (elapsed >= ticks) {
                    double max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null ? 20
                            : player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                    player.setHealth(Math.min(max, player.getHealth() + heal));
                    removeOne(player, "medkit");
                    plugin.messages().send(player, "special.medkit-done");
                    plugin.messages().sound(player, "join");
                    channeling.remove(player.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 5L, 5L).getTaskId();
        channeling.put(player.getUniqueId(), taskId);
        return true;
    }

    private boolean useShieldPotion(Player player, ItemStack item) {
        ConfigurationSection section = plugin.configs().items().getConfigurationSection("specials.shield-potion");
        if (section == null) return true;
        if (onCooldown(player, "shield", section.getInt("cooldown-ticks", 40))) return true;
        addEffect(player, "absorption", section.getInt("absorption-seconds", 90) * 20,
                section.getInt("absorption-level", 1));
        addEffect(player, "resistance", section.getInt("resistance-seconds", 8) * 20, 1);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
        plugin.messages().sound(player, "join");
        item.setAmount(item.getAmount() - 1);
        setCooldown(player, "shield", section.getInt("cooldown-ticks", 40));
        return true;
    }

    private boolean useBandage(Player player, ItemStack item) {
        ConfigurationSection section = plugin.configs().items().getConfigurationSection("specials.bandage");
        if (section == null) return true;
        if (onCooldown(player, "bandage", section.getInt("cooldown-ticks", 0))) return true;
        heal(player, section.getDouble("instant-heal", 4.0));
        addEffect(player, "regeneration", section.getInt("regen-seconds", 8) * 20, section.getInt("amplifier", 1));
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0);
        plugin.messages().sound(player, "join");
        item.setAmount(item.getAmount() - 1);
        setCooldown(player, "bandage", section.getInt("cooldown-ticks", 0));
        return true;
    }

    private boolean useShield(Player player, ItemStack item, String id) {
        ConfigurationSection section = plugin.configs().items().getConfigurationSection("specials." + id);
        if (section == null) return true;
        addEffect(player, "absorption", section.getInt("absorption-seconds", 120) * 20,
                section.getInt("absorption-level", 2));
        addEffect(player, "resistance", section.getInt("resistance-seconds", 10) * 20, 0);
        consume(item);
        return true;
    }

    private boolean useChugJug(Player player, ItemStack item) {
        ConfigurationSection section = plugin.configs().items().getConfigurationSection("specials.chug-jug");
        if (section == null || channeling.containsKey(player.getUniqueId())) return true;
        int ticks = section.getInt("channel-ticks", 100);
        Location origin = player.getLocation().clone();
        int taskId = new BukkitRunnable() {
            int elapsed;
            @Override public void run() {
                if (!player.isOnline() || player.isDead() || player.getLocation().distanceSquared(origin) > 1.5) {
                    channeling.remove(player.getUniqueId()); cancel(); return;
                }
                elapsed += 5;
                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 4);
                if (elapsed < ticks) return;
                heal(player, 100);
                addEffect(player, "absorption", section.getInt("absorption-seconds", 240) * 20,
                        section.getInt("absorption-level", 4));
                removeOne(player, "chug-jug");
                channeling.remove(player.getUniqueId()); cancel();
            }
        }.runTaskTimer(plugin, 5, 5).getTaskId();
        channeling.put(player.getUniqueId(), taskId);
        return true;
    }

    private boolean useRift(Player player, ItemStack item) {
        ConfigurationSection section = plugin.configs().items().getConfigurationSection("specials.rift");
        player.setVelocity(new Vector(0, section == null ? 2.6 : section.getDouble("height", 2.6), 0));
        plugin.game().markNoFallDamage(player);
        consume(item);
        return true;
    }

    private boolean useAdrenaline(Player player, ItemStack item) {
        ConfigurationSection section = plugin.configs().items().getConfigurationSection("specials.adrenaline");
        int ticks = (section == null ? 20 : section.getInt("seconds", 20)) * 20;
        addEffect(player, "speed", ticks, 1);
        addEffect(player, "strength", ticks, 0);
        addEffect(player, "regeneration", ticks, 0);
        consume(item);
        return true;
    }

    private boolean throwSpecial(Player player, ItemStack item, String id, double speed) {
        Snowball projectile = player.launchProjectile(Snowball.class);
        projectile.getPersistentDataContainer().set(Keys.SPECIAL, PersistentDataType.STRING, id);
        projectile.setVelocity(player.getEyeLocation().getDirection().multiply(speed));
        consume(item);
        return true;
    }

    private boolean useImpulse(Player player, ItemStack item) {
        ConfigurationSection section = plugin.configs().items().getConfigurationSection("specials.impulse");
        if (section == null) return true;
        if (onCooldown(player, "impulse", section.getInt("cooldown-ticks", 60))) return true;
        double radius = section.getDouble("radius", 6.0);
        double power = section.getDouble("power", 1.8);
        Location center = player.getLocation();
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player other)) continue;
            Vector push = other.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.01) push = new Vector(0, 1, 0);
            push.normalize().multiply(power);
            push.setY(Math.max(0.5, push.getY()));
            other.setVelocity(push);
            plugin.game().markNoFallDamage(other);
        }
        Vector self = player.getLocation().getDirection().multiply(-0.4).setY(power * 0.6);
        player.setVelocity(self);
        plugin.game().markNoFallDamage(player);
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 1);
        player.playSound(center, "entity.generic.explode", 1f, 1.4f);
        item.setAmount(item.getAmount() - 1);
        setCooldown(player, "impulse", section.getInt("cooldown-ticks", 60));
        return true;
    }

    private boolean throwSmoke(Player player, ItemStack item) {
        Snowball ball = player.launchProjectile(Snowball.class);
        ball.getPersistentDataContainer().set(Keys.SPECIAL, PersistentDataType.STRING, "smoke-bomb");
        ball.setVelocity(player.getLocation().getDirection().multiply(1.3));
        player.playSound(player.getLocation(), "entity.snowball.throw", 1f, 1f);
        item.setAmount(item.getAmount() - 1);
        return true;
    }

    public void onProjectileHit(Projectile projectile) {
        String id = projectile.getPersistentDataContainer().get(Keys.SPECIAL, PersistentDataType.STRING);
        if (id == null) return;
        if (!"smoke-bomb".equals(id)) {
            activateThrowable(projectile, id);
            return;
        }
        ConfigurationSection section = plugin.configs().items().getConfigurationSection("specials.smoke-bomb");
        double radius = section == null ? 5.0 : section.getDouble("radius", 5.0);
        int blind = section == null ? 5 : section.getInt("blind-seconds", 5);
        Location location = projectile.getLocation();
        location.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, location, 120, radius / 2, 1.5, radius / 2,
                0.02);
        location.getWorld().playSound(location, "entity.generic.extinguish_fire", 1f, 0.7f);
        for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (!(entity instanceof Player other)) continue;
            if (projectile.getShooter() instanceof Player shooter && shooter.equals(other)) continue;
            addEffect(other, "blindness", blind * 20, 0);
            addEffect(other, "darkness", blind * 20, 0);
        }
    }

    private void activateThrowable(Projectile projectile, String id) {
        ConfigurationSection section = plugin.configs().items().getConfigurationSection("specials." + id);
        if (section == null) return;
        Location center = projectile.getLocation();
        if (id.equals("launch-pad")) {
            Block base = center.clone().subtract(0, 1, 0).getBlock();
            int y = base.getY();
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                Block block = base.getWorld().getBlockAt(base.getX() + x, y, base.getZ() + z);
                plugin.regeneration().record(block);
                block.setType(Material.SLIME_BLOCK, false);
            }
            return;
        }
        if (id.equals("port-a-fort")) {
            int size = section.getInt("size", 2);
            int height = section.getInt("height", 4);
            Material material = material(section.getString("block", "STONE_BRICKS"), Material.STONE_BRICKS);
            for (int y = 0; y < height; y++) for (int x = -size; x <= size; x++) for (int z = -size; z <= size; z++) {
                if (y > 0 && Math.abs(x) < size && Math.abs(z) < size) continue;
                Block block = center.getBlock().getRelative(x, y, z);
                plugin.regeneration().record(block); block.setType(material, false);
            }
            return;
        }
        if (id.equals("fireball") || id.equals("cluster-bomb")) {
            explosive(projectile, section, id);
            return;
        }
        if (id.equals("lightning-grenade")) {
            center.getWorld().strikeLightningEffect(center);
            double r = section.getDouble("radius", 5);
            double damage = section.getDouble("damage", 6.0);
            for (Entity entity : center.getWorld().getNearbyEntities(center, r, r, r)) {
                if (entity instanceof LivingEntity living && !living.equals(projectile.getShooter())) {
                    living.damage(damage, shooterOf(projectile));
                }
            }
            return;
        }
        if (id.equals("freeze-grenade")) {
            double r = section.getDouble("radius", 6);
            int seconds = section.getInt("seconds", 6);
            center.getWorld().spawnParticle(Particle.SNOWFLAKE, center, 120, r / 2, 1.2, r / 2, 0.02);
            center.getWorld().playSound(center, "block.glass.break", 1f, 1.4f);
            for (Entity entity : center.getWorld().getNearbyEntities(center, r, r, r)) {
                if (!(entity instanceof LivingEntity living)) continue;
                if (living.equals(projectile.getShooter())) continue;
                living.setFreezeTicks(seconds * 20);
                addEffect(living instanceof Player p ? p : null, "slowness", seconds * 20, 3);
                addEffect(living instanceof Player p ? p : null, "mining_fatigue", seconds * 20, 2);
            }
            return;
        }
        if (id.equals("healing-grenade")) {
            double r = section.getDouble("radius", 6);
            int seconds = section.getInt("seconds", 6);
            center.getWorld().spawnParticle(Particle.HEART, center, 40, r / 2, 1.2, r / 2, 0.02);
            center.getWorld().playSound(center, "entity.player.levelup", 1f, 1.6f);
            for (Entity entity : center.getWorld().getNearbyEntities(center, r, r, r)) {
                if (entity instanceof Player p) {
                    addEffect(p, "regeneration", seconds * 20, 1);
                    addEffect(p, "absorption", seconds * 20 * 2, 1);
                }
            }
            return;
        }
        double radius = section.getDouble("radius", 7);
        double power = section.getDouble("power", 2.4);
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (id.equals("boogie-bomb")) {
                addEffect(living instanceof Player p ? p : null, "slowness", section.getInt("seconds", 6) * 20, 4);
                addEffect(living instanceof Player p ? p : null, "jump_boost", section.getInt("seconds", 6) * 20, 2);
                continue;
            }
            Vector push = living.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.01) push = new Vector(0, 1, 0);
            push.normalize().multiply(power).setY(Math.max(0.7, push.getY()));
            living.setVelocity(push);
            if (living instanceof Player p) plugin.game().markNoFallDamage(p);
        }
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 2);
    }

    /** Launch pad: chamado quando o jogador pisa em um slime block do BR. */
    public void launch(Player player) {
        ConfigurationSection section = plugin.configs().items().getConfigurationSection("specials.launch-pad");
        if (section == null || !section.getBoolean("enabled", true)) return;
        if (onCooldown(player, "launchpad", 20)) return;
        Vector velocity = player.getLocation().getDirection().setY(0);
        if (velocity.lengthSquared() > 0) {
            velocity.normalize().multiply(section.getDouble("forward", 0.9));
        }
        velocity.setY(section.getDouble("power", 1.6));
        player.setVelocity(velocity);
        plugin.game().markNoFallDamage(player);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 20, 0.4, 0.1, 0.4, 0.05);
        player.playSound(player.getLocation(), "entity.slime.squish", 1f, 1.2f);
        setCooldown(player, "launchpad", 20);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void removeOne(Player player, String id) {
        for (ItemStack content : player.getInventory().getContents()) {
            if (content == null) continue;
            if (id.equals(idOf(content))) {
                content.setAmount(content.getAmount() - 1);
                return;
            }
        }
    }

    public void addEffect(Player player, String key, int ticks, int amplifier) {
        if (player == null) return;
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(key));
        if (type == null) return;
        player.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true));
    }

    private void heal(Player player, double amount) {
        var attribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double max = attribute == null ? 20 : attribute.getValue();
        player.setHealth(Math.min(max, player.getHealth() + amount));
    }

    private void consume(ItemStack item) {
        item.setAmount(Math.max(0, item.getAmount() - 1));
    }

    private boolean onCooldown(Player player, String key, int ticks) {
        Long until = cooldowns.get(cooldownKey(player, key));
        return until != null && until > System.currentTimeMillis();
    }

    private void setCooldown(Player player, String key, int ticks) {
        cooldowns.put(cooldownKey(player, key), System.currentTimeMillis() + ticks * 50L);
    }

    private UUID cooldownKey(Player player, String key) {
        return UUID.nameUUIDFromBytes((player.getUniqueId() + ":" + key).getBytes());
    }

    public void clear(UUID uuid) {
        Integer task = channeling.remove(uuid);
        if (task != null) {
            Bukkit.getScheduler().cancelTask(task);
        }
    }

    public static Material material(String name, Material fallback) {
        if (name == null) return fallback;
        Material material = Material.matchMaterial(name.toUpperCase());
        return material == null ? fallback : material;
    }

}
