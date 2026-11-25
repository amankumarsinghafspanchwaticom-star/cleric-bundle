package com.arachnaid.item;

import com.arachnaid.ArachnaidPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class ArachnaidItemFactory {

    private final ArachnaidPlugin plugin;
    private final NamespacedKey axeKey;

    public ArachnaidItemFactory(ArachnaidPlugin plugin) {
        this.plugin = plugin;
        this.axeKey = new NamespacedKey(plugin, "arachnaid_axe");
    }

    /**
     * Builds a vanilla-stat Netherite Axe with:
     * - Zero enchantments and zero damage/attack-speed attribute changes.
     * - PersistentDataContainer tag so renamed normal axes cannot spoof abilities.
     * - CustomModelDataComponent string for the 1.21.11 resource pack (gracefully falls back to
     *   vanilla Netherite Axe if a player does not have the resource pack equipped).
     */
    public ItemStack createArachnaidAxe() {
        ItemStack axe = new ItemStack(Material.NETHERITE_AXE, 1);
        ItemMeta meta = axe.getItemMeta();
        if (meta == null) {
            return axe;
        }

        String nameRaw = plugin.getConfig().getString("weapon.name", "<dark_red><bold>ARACHNAID AXE</bold></dark_red>");
        Component displayName = plugin.parse(nameRaw)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        meta.displayName(displayName);

        List<String> loreRaw = plugin.getConfig().getStringList("weapon.lore");
        if (!loreRaw.isEmpty()) {
            List<Component> lore = loreRaw.stream()
                    .map(line -> plugin.parse(line)
                            .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
                    .toList();
            meta.lore(lore);
        }

        String modelId = plugin.getConfig().getString("weapon.custom-model-data", "arachnaid_axe");
        if (modelId != null && !modelId.isBlank()) {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of(modelId));
            meta.setCustomModelDataComponent(cmd);
        }

        meta.getPersistentDataContainer().set(axeKey, PersistentDataType.BYTE, (byte) 1);
        axe.setItemMeta(meta);
        return axe;
    }

    public boolean isArachnaidAxe(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_AXE || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(axeKey, PersistentDataType.BYTE);
    }
}
