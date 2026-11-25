package com.arachnaid.listener;

import com.arachnaid.ArachnaidPlugin;
import com.arachnaid.item.ArachnaidItemFactory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;

public final class ItemProtectionListener implements Listener {

    private final ArachnaidPlugin plugin;
    private final ArachnaidItemFactory itemFactory;

    public ItemProtectionListener(ArachnaidPlugin plugin, ArachnaidItemFactory itemFactory) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (itemFactory.isArachnaidAxe(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (itemFactory.isArachnaidAxe(event.getItem())) {
            event.setCancelled(true);
            event.getEnchanter().sendActionBar(plugin.rawMsg("enchant-blocked"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack first = inv.getFirstItem();
        ItemStack second = inv.getSecondItem();
        ItemStack result = event.getResult();

        boolean involvesAxe = itemFactory.isArachnaidAxe(first) || itemFactory.isArachnaidAxe(second);
        if (!involvesAxe || result == null) {
            return;
        }

        boolean secondIsEnchantedBook = second != null && second.getType() == Material.ENCHANTED_BOOK;
        boolean secondHasEnchants = second != null && !second.getEnchantments().isEmpty();
        boolean resultHasEnchants = !result.getEnchantments().isEmpty();

        if (secondIsEnchantedBook || secondHasEnchants || resultHasEnchants) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnvilResultClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory)) {
            return;
        }
        if (event.getRawSlot() != 2) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (itemFactory.isArachnaidAxe(clicked) && !clicked.getEnchantments().isEmpty()) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendActionBar(plugin.rawMsg("enchant-blocked"));
            }
        }
    }
}
