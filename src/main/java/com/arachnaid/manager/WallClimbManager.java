package com.arachnaid.manager;

import com.arachnaid.ArachnaidPlugin;
import com.arachnaid.item.ArachnaidItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WallClimbManager {

    private static final BlockFace[] CARDINALS = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final ArachnaidPlugin plugin;
    private final ArachnaidItemFactory itemFactory;
    private final Map<UUID, ClimbSession> activeSessions = new ConcurrentHashMap<>();
    private BukkitTask climbTask;

    public WallClimbManager(ArachnaidPlugin plugin, ArachnaidItemFactory itemFactory) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
    }

    public void startTask() {
        climbTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public boolean isClimbing(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public void startClimbing(Player player) {
        UUID id = player.getUniqueId();
        if (activeSessions.containsKey(id)) {
            return;
        }

        boolean prevAllowFlight = player.getAllowFlight();
        activeSessions.put(id, new ClimbSession(prevAllowFlight));

        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            player.setAllowFlight(true);
            player.setFlying(false);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 0.7f, 1.4f);
        player.sendActionBar(plugin.rawMsg("climb-start"));
    }

    public void stopClimbing(Player player, boolean cancelledByDoubleJump) {
        ClimbSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            player.setFlying(false);
            player.setAllowFlight(session.previousAllowFlight());
        }

        if (cancelledByDoubleJump) {
            double push = plugin.getConfig().getDouble("wall-climb.cancel-push-strength", 0.32);
            Vector away = player.getLocation().getDirection().setY(0);
            if (away.lengthSquared() > 0.001) {
                away.normalize().multiply(-push).setY(0.28);
                player.setVelocity(away);
            } else {
                player.setVelocity(new Vector(0, 0.28, 0));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_SPIDER_HURT, 0.6f, 1.5f);
            player.sendActionBar(plugin.rawMsg("climb-cancelled"));
        } else if (player.isOnline()) {
            player.sendActionBar(plugin.rawMsg("climb-stop"));
        }
    }

    public void stopAll() {
        if (climbTask != null) {
            climbTask.cancel();
            climbTask = null;
        }
        for (UUID id : activeSessions.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                stopClimbing(player, false);
            }
        }
        activeSessions.clear();
    }

    private void tick() {
        if (activeSessions.isEmpty()) {
            return;
        }

        double attachDist = plugin.getConfig().getDouble("wall-climb.wall-attach-distance", 0.85);
        double climbSpeed = plugin.getConfig().getDouble("wall-climb.climb-speed", 0.26);

        Iterator<Map.Entry<UUID, ClimbSession>> it = activeSessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ClimbSession> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            ClimbSession session = entry.getValue();

            if (player == null || !player.isOnline() || player.isDead()) {
                it.remove();
                continue;
            }

            if (!itemFactory.isArachnaidAxe(player.getInventory().getItemInMainHand())) {
                stopClimbing(player, false);
                continue;
            }

            if (!isNearSolidWall(player, attachDist)) {
                stopClimbing(player, false);
                continue;
            }

            if ((player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                    && player.isFlying()) {
                player.setFlying(false);
            }

            Vector currentVel = player.getVelocity();
            float pitch = player.getLocation().getPitch();
            double verticalSpeed = (pitch > 65.0f) ? 0.04 : climbSpeed;

            player.setVelocity(new Vector(currentVel.getX() * 0.6, verticalSpeed, currentVel.getZ() * 0.6));
            player.setFallDistance(0f);

            session.incrementTick();
            if (session.ticks() % 6 == 0) {
                Location loc = player.getLocation();
                World world = player.getWorld();
                world.playSound(loc, Sound.ENTITY_SPIDER_STEP, 0.35f, 1.3f);
                world.spawnParticle(Particle.BLOCK, loc.clone().add(0, 0.8, 0), 4,
                        0.2, 0.2, 0.2, Material.COBWEB.createBlockData());
            }
        }
    }

    public boolean isNearSolidWall(Player player, double distance) {
        Location base = player.getLocation();
        World world = base.getWorld();
        if (world == null) {
            return false;
        }

        double[] yOffsets = {0.2, 1.1};
        for (double yOff : yOffsets) {
            for (BlockFace face : CARDINALS) {
                double checkX = base.getX() + face.getModX() * distance;
                double checkY = base.getY() + yOff;
                double checkZ = base.getZ() + face.getModZ() * distance;
                Block block = world.getBlockAt(
                        Location.locToBlock(checkX),
                        Location.locToBlock(checkY),
                        Location.locToBlock(checkZ)
                );
                if (block.getType().isSolid() && block.getType() != Material.COBWEB) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class ClimbSession {
        private final boolean previousAllowFlight;
        private int ticks = 0;

        private ClimbSession(boolean previousAllowFlight) {
            this.previousAllowFlight = previousAllowFlight;
        }

        boolean previousAllowFlight() {
            return previousAllowFlight;
        }

        int ticks() {
            return ticks;
        }

        void incrementTick() {
            ticks++;
        }
    }
}
