package com.smp.br.loot;

public enum Rarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY;

    public static Rarity parse(String value, Rarity fallback) {
        if (value == null) return fallback;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
