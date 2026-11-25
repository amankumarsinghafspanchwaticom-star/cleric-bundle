package com.arachnaid.manager;

import com.arachnaid.ArachnaidPlugin;
import com.arachnaid.item.ArachnaidItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ShapedRecipe;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Tracks whether the one craftable ARACHNAID AXE has been crafted/claimed.
 * - Starts craftable (axeActive = false).
 * - Immediately locks crafting as soon as 1 axe is crafted (or given).
 * - Automatically unlocks crafting again if the axe despawns, falls into the void, or breaks.
 */
public final class AxeStateManager {

    private final ArachnaidPlugin plugin;
    private final ArachnaidItemFactory itemFactory;
    private final File file;
    private final NamespacedKey recipeKey;
    private boolean axeActive = false;

    public AxeStateManager(ArachnaidPlugin plugin, ArachnaidItemFactory itemFactory) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.file = new File(plugin.getDataFolder(), "state.yml");
        this.recipeKey = new NamespacedKey(plugin, "arachnaid_axe_recipe");
    }

    public NamespacedKey getRecipeKey() {
        return recipeKey;
    }

    public boolean isAxeActive() {
        return axeActive;
    }

    public void load() {
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            this.axeActive = yaml.getBoolean("axe-active", false);
        } else {
            this.axeActive = false;
            save();
        }
        syncRecipeState();
    }

    public void save() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("axe-active", axeActive);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save state.yml: " + e.getMessage());
        }
    }

    public void markAxeClaimed() {
        this.axeActive = true;
        save();
        syncRecipeState();
    }

    public void markAxeLostAndUnlock(String reason, boolean broadcast) {
        if (!this.axeActive) {
            return;
        }
        this.axeActive = false;
        save();
        syncRecipeState();

        if (broadcast && plugin.getConfig().getBoolean("crafting.broadcast-on-unlock", true)) {
            String prefix = plugin.getConfig().getString("messages.prefix", "");
            String raw = plugin.getConfig().getString("messages.craft-unlocked-broadcast", "")
                    .replace("{reason}", reason);
            Bukkit.broadcast(plugin.parse(prefix + raw));
        }
    }

    public void forceUnlock() {
        this.axeActive = false;
        save();
        syncRecipeState();
    }

    public void syncRecipeState() {
        Bukkit.removeRecipe(recipeKey);

        boolean craftingEnabled = plugin.getConfig().getBoolean("crafting.enabled", true);
        if (!axeActive && craftingEnabled) {
            ShapedRecipe recipe = buildRecipeFromConfig();
            if (recipe != null) {
                Bukkit.addRecipe(recipe);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.discoverRecipe(recipeKey);
                }
            }
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.undiscoverRecipe(recipeKey);
            }
        }
    }

    public void syncPlayerRecipeBook(Player player) {
        boolean craftingEnabled = plugin.getConfig().getBoolean("crafting.enabled", true);
        if (!axeActive && craftingEnabled) {
            player.discoverRecipe(recipeKey);
        } else {
            player.undiscoverRecipe(recipeKey);
        }
    }

    private ShapedRecipe buildRecipeFromConfig() {
        List<String> shape = plugin.getConfig().getStringList("crafting.shape");
        if (shape.size() != 3) {
            plugin.getLogger().warning("Invalid crafting.shape in config.yml (must have 3 rows). Using default.");
            shape = List.of("WCW", "CAC", "WFW");
        }

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, itemFactory.createArachnaidAxe());
        recipe.shape(shape.get(0), shape.get(1), shape.get(2));

        ConfigurationSection ingSection = plugin.getConfig().getConfigurationSection("crafting.ingredients");
        if (ingSection != null) {
            for (String key : ingSection.getKeys(false)) {
                if (key.length() != 1) {
                    continue;
                }
                String matName = ingSection.getString(key, "");
                Material mat = Material.matchMaterial(matName);
                if (mat == null || mat.isAir()) {
                    plugin.getLogger().warning("Invalid crafting ingredient '" + matName + "' for key '" + key + "'.");
                    continue;
                }
                recipe.setIngredient(key.charAt(0), mat);
            }
        } else {
            recipe.setIngredient('W', Material.COBWEB);
            recipe.setIngredient('C', Material.FERMENTED_SPIDER_EYE);
            recipe.setIngredient('A', Material.NETHERITE_AXE);
            recipe.setIngredient('F', Material.NETHERITE_INGOT);
        }

        return recipe;
    }
}
