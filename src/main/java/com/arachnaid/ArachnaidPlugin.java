package com.arachnaid;

import com.arachnaid.command.ArachnaidCommand;
import com.arachnaid.item.ArachnaidItemFactory;
import com.arachnaid.listener.AxeLifecycleListener;
import com.arachnaid.listener.ItemProtectionListener;
import com.arachnaid.listener.SpecialWebProtectionListener;
import com.arachnaid.listener.SpiderWebAbilityListener;
import com.arachnaid.listener.WallClimbListener;
import com.arachnaid.manager.AxeStateManager;
import com.arachnaid.manager.CooldownManager;
import com.arachnaid.manager.SpecialWebManager;
import com.arachnaid.manager.WallClimbManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArachnaidPlugin extends JavaPlugin {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private ArachnaidItemFactory itemFactory;
    private AxeStateManager axeStateManager;
    private CooldownManager cooldownManager;
    private SpecialWebManager specialWebManager;
    private WallClimbManager wallClimbManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.itemFactory = new ArachnaidItemFactory(this);
        this.axeStateManager = new AxeStateManager(this, itemFactory);
        this.cooldownManager = new CooldownManager(this);
        this.specialWebManager = new SpecialWebManager(this);
        this.wallClimbManager = new WallClimbManager(this, itemFactory);

        axeStateManager.load();
        cooldownManager.load();
        specialWebManager.load();
        wallClimbManager.startTask();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new AxeLifecycleListener(this, itemFactory, axeStateManager), this);
        pm.registerEvents(new ItemProtectionListener(this, itemFactory), this);
        pm.registerEvents(new SpiderWebAbilityListener(this, itemFactory, cooldownManager, specialWebManager), this);
        pm.registerEvents(new WallClimbListener(this, itemFactory, wallClimbManager), this);
        pm.registerEvents(new SpecialWebProtectionListener(this, specialWebManager), this);

        PluginCommand cmd = getCommand("arachnaid");
        if (cmd != null) {
            ArachnaidCommand executor = new ArachnaidCommand(this, itemFactory, axeStateManager, cooldownManager);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("ArachnaidAxe enabled. Craft locked (axe exists): " + axeStateManager.isAxeActive());
    }

    @Override
    public void onDisable() {
        if (wallClimbManager != null) {
            wallClimbManager.stopAll();
        }
        if (axeStateManager != null) {
            axeStateManager.save();
        }
        if (cooldownManager != null) {
            cooldownManager.save();
        }
        if (specialWebManager != null) {
            specialWebManager.save();
        }
    }

    public Component msg(String path) {
        String prefix = getConfig().getString("messages.prefix", "");
        String raw = getConfig().getString("messages." + path, "");
        return MINI_MESSAGE.deserialize(prefix + raw);
    }

    public Component rawMsg(String path) {
        String raw = getConfig().getString("messages." + path, "");
        return MINI_MESSAGE.deserialize(raw);
    }

    public Component parse(String miniMessage) {
        return MINI_MESSAGE.deserialize(miniMessage);
    }
}
