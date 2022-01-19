package ru.violence.teleporteffectizer;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.spigotmc.event.entity.EntityDismountEvent;
import org.spigotmc.event.entity.EntityMountEvent;
import ru.violence.teleporteffectizer.event.TeleportEffectEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportEffectizerPlugin extends JavaPlugin implements Listener {
    private double radius;
    private boolean ignoreSender;
    private Particle particleType;
    private int particleCount;
    private double particleOffsetX;
    private double particleOffsetY;
    private double particleOffsetZ;
    private double particleExtra;

    private boolean soundEnabled;
    private Sound soundType;
    private float soundVolume;
    private float soundPitch;

    private final Map<UUID, BukkitTask> ignoredPlayers = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPlugin();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void reloadPlugin() {
        radius = getConfig().getDouble("radius");
        ignoreSender = getConfig().getBoolean("ignore-sender");
        particleType = Particle.valueOf(getConfig().getString("particle.type"));
        particleCount = getConfig().getInt("particle.count");
        particleOffsetX = getConfig().getDouble("particle.offset-x");
        particleOffsetY = getConfig().getDouble("particle.offset-y");
        particleOffsetZ = getConfig().getDouble("particle.offset-z");
        particleExtra = getConfig().getDouble("particle.extra");

        soundEnabled = getConfig().getBoolean("sound.enabled");
        soundType = Sound.valueOf(getConfig().getString("sound.type"));
        soundVolume = (float) getConfig().getDouble("sound.volume");
        soundPitch = (float) getConfig().getDouble("sound.pitch");
    }

    public void ignorePlayer(Player player, int delay) {
        UUID uniqueId = player.getUniqueId();
        BukkitTask expireTask = Bukkit.getScheduler().runTaskLater(this, () -> ignoredPlayers.remove(uniqueId), delay);
        BukkitTask prevTask = ignoredPlayers.put(uniqueId, expireTask);
        if (prevTask != null) prevTask.cancel();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        ignorePlayer(event.getPlayer(), 1);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        ignorePlayer(event.getPlayer(), 1);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityMountEvent(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        ignorePlayer((Player) event.getEntity(), 1);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDismountEvent(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        ignorePlayer((Player) event.getEntity(), 1);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        ignorePlayer(event.getEntity(), 1);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            playEffect(event.getPlayer(), event.getTo().getX(), event.getTo().getY(), event.getTo().getZ());
        } else {
            ignorePlayer(event.getPlayer(), 10);
        }
    }

    private void playEffect(Player player, double x, double y, double z) {
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (ignoredPlayers.containsKey(player.getUniqueId())) return;
        if (!callEvent(player, x, y, z)) return;

        y += 1;
        World world = player.getWorld();
        Location loc = new Location(world, x, y, z);

        for (Player receiver : world.getNearbyPlayers(loc, radius)) {
            if (receiver == player && ignoreSender) continue;
            if (!receiver.canSee(player)) continue;

            receiver.spawnParticle(particleType, x, y, z, particleCount, particleOffsetX, particleOffsetY, particleOffsetZ, particleExtra, null);
            if (soundEnabled) {
                receiver.playSound(loc, soundType, soundVolume, soundPitch);
            }
        }
    }

    private boolean callEvent(Player player, double x, double y, double z) {
        if (TeleportEffectEvent.getHandlerList().getRegisteredListeners().length == 0) return true;
        return new TeleportEffectEvent(player, x, y, z).callEvent();
    }
}
