package com.smp.br.util;

import com.smp.br.BattleRoyalePlugin;
import org.bukkit.NamespacedKey;

public final class Keys {

    public static NamespacedKey SPECIAL;
    public static NamespacedKey USES;
    public static NamespacedKey TEMP;
    public static NamespacedKey RARITY;
    public static NamespacedKey OWNER;

    private Keys() {
    }

    public static void init(BattleRoyalePlugin plugin) {
        SPECIAL = new NamespacedKey(plugin, "special");
        USES = new NamespacedKey(plugin, "uses");
        TEMP = new NamespacedKey(plugin, "temporary");
        RARITY = new NamespacedKey(plugin, "rarity");
        OWNER = new NamespacedKey(plugin, "owner");
    }
}
