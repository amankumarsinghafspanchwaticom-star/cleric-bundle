package com.arachnaid.listener;

import com.arachnaid.ArachnaidPlugin;
import com.arachnaid.item.ArachnaidItemFactory;
import com.arachnaid.manager.WallClimbManager;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WallClimbListener implements Listener {

    private final ArachnaidPlugin plugin;
    private final ArachnaidItemFactory itemFactory;
    private final WallClimbManager climbManager;
    private final Map<UUID, Long> lastWallClick = new ConcurrentHashMap<>();

    public WallClimbListener(
            ArachnaidPlugin plugin,
            ArachnaidItemFactory itemFactory,
            WallClimbManager climbManager
    ) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.climbManager = climbManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClickWall(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!itemFactory.isArachnaidAxe(event.getItem())) {
            return;
        }

        Player player = event.getPlayer();
        double minPitch = plugin.getConfig().getDouble("spider-web.min-look-down-pitch", 45.0);
        if (player.isSneaking() && player.getLocation().getPitch() >= minPitch) {
            return;
        }

        double maxAim = plugin.getConfig().getDouble("wall-climb.max-wall-aim-distance", 3.0);
        RayTraceResult ray = player.rayTraceBlocks(maxAim, FluidCollisionMode.NEVER);
        if (ray == null || ray.getHitBlock() == null || ray.getHitBlockFace() == null) {
            return;
        }

        Block hitBlock = ray.getHitBlock();
        BlockFace hitFace = ray.getHitBlockFace();
        if (!hitBlock.getType().isSolid() || hitBlock.getType() == Material.COBWEB || !isHorizontalFace(hitFace)) {
            return;
        }

        event.setCancelled(true);

        if (!player.hasPermission("arachnaid.use.climb")) {
            player.sendActionBar(plugin.rawMsg("no-permission"));
            return;
        }

        long now = System.currentTimeMillis();
        long window = plugin.getConfig().getLong("wall-climb.double-click-window-ms", 400L);
        Long previous = lastWallClick.put(player.getUniqueId(), now);

        if (previous != null && (now - previous) <= window) {
            lastWallClick.remove(player.getUniqueId());
            if (!climbManager.isClimbing(player)) {
                climbManager.startClimbing(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDoubleJumpCancel(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!climbManager.isClimbing(player)) {
            return;
        }
        event.setCancelled(true);
        climbManager.stopClimbing(player, true);
    }

    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!climbManager.isClimbing(player)) {
            return;
        }
        ItemStack nextItem = player.getInventory().getItem(event.getNewSlot());
        if (!itemFactory.isArachnaidAxe(nextItem)) {
            climbManager.stopClimbing(player, false);
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (climbManager.isClimbing(player) && !itemFactory.isArachnaidAxe(event.getMainHandItem())) {
            climbManager.stopClimbing(player, false);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (climbManager.isClimbing(player)) {
            climbManager.stopClimbing(player, false);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        climbManager.stopClimbing(event.getPlayer(), false);
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        climbManager.stopClimbing(event.getPlayer(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastWallClick.remove(event.getPlayer().getUniqueId());
        climbManager.stopClimbing(event.getPlayer(), false);
    }

    private boolean isHorizontalFace(BlockFace face) {
        return face == BlockFace.NORTH
                || face == BlockFace.SOUTH
                || face == BlockFace.EAST
                || face == BlockFace.WEST;
    }
}
