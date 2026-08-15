package com.smp.br.loot;

import java.util.List;

public record LootEntry(String material, int min, int max, double weight, List<String> enchants, String potion) {
}
