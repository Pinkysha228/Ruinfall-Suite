package ru.ruinfall.radiation;

import ru.ruinfall.RuinfallFeature;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.UUID;

public final class RadiationZoneController extends RuinfallFeature implements Listener {
    public RadiationZoneController(org.bukkit.plugin.java.JavaPlugin plugin) {
        super(plugin, "radiation-zones.yml");
    }

    private final HashMap<UUID, Long> soundCooldown = new HashMap<>();

    @Override
    public void enable() {

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            FileConfiguration config = getConfig();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isOutsideNormalZone(player.getLocation(), config)) spawnRadiationParticles(player, config);
            }
        }, 0L, 3L);

        logInfo("запущен. зоны радиации активны.");
    }

    @Override
    public void disable() {
        soundCooldown.clear();
        logInfo("отключён.");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location location = player.getLocation();
        FileConfiguration config = getConfig();

        if (isOutsideDeadZone(location, config)) {
            player.setHealth(0);
            logEvent(player.getName() + " пересёк смертельную границу.");
            return;
        }

        if (!isOutsideNormalZone(location, config)) return;

        long now = System.currentTimeMillis();
        long cooldown = getConfig().getLong("audio.cooldown-seconds", 9) * 1000L;
        if (now - soundCooldown.getOrDefault(player.getUniqueId(), 0L) > cooldown) {
            String sound = getConfig().getString("audio.sound", "custom.radiation");
            float volume = (float) getConfig().getDouble("audio.volume", 1.0);
            float pitch = (float) getConfig().getDouble("audio.pitch", 1.0);
            player.playSound(location, sound, SoundCategory.MASTER, volume, pitch);
            soundCooldown.put(player.getUniqueId(), now);
        }

        if (location.getBlock().getType() == Material.WATER) {
            int duration = getConfig().getInt("effects.poison-duration", 45);
            int amplifier = getConfig().getInt("effects.poison-amplifier", 7);
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, amplifier));
        }
    }

    private void spawnRadiationParticles(Player player, FileConfiguration config) {
        Location playerLocation = player.getLocation();
        World world = playerLocation.getWorld();
        if (world == null) return;

        int count = config.getInt("particles.count-per-player", 60);
        double maxRadius = config.getDouble("particles.max-radius", 150);
        double y = config.getDouble("particles.y", 62);
        double spreadY = config.getDouble("particles.spread-y", 0.5);

        for (int i = 0; i < count; i++) {
            double randomRadius = Math.pow(Math.random(), 2) * maxRadius;
            double angle = Math.random() * 2 * Math.PI;
            double x = playerLocation.getX() + randomRadius * Math.cos(angle);
            double z = playerLocation.getZ() + randomRadius * Math.sin(angle);

            if (isStrictlyOutside(x, z, config) && new Location(world, x, y, z).getBlock().getType() == Material.WATER) {
                player.spawnParticle(Particle.CLOUD, x, y, z, 5, 0.0, spreadY, 0.0, 0.1);
            }
        }
    }

    private boolean isStrictlyOutside(double x, double z, FileConfiguration config) {
        double buffer = 0.8;
        return x < config.getDouble("points.minX") - buffer
                || x > config.getDouble("points.maxX") + buffer
                || z < config.getDouble("points.minZ") - buffer
                || z > config.getDouble("points.maxZ") + buffer;
    }

    private boolean isOutsideNormalZone(Location location, FileConfiguration config) {
        return location.getX() < config.getDouble("points.minX")
                || location.getX() > config.getDouble("points.maxX")
                || location.getZ() < config.getDouble("points.minZ")
                || location.getZ() > config.getDouble("points.maxZ");
    }

    private boolean isOutsideDeadZone(Location location, FileConfiguration config) {
        return location.getX() < config.getDouble("points.minX2")
                || location.getX() > config.getDouble("points.maxX2")
                || location.getZ() < config.getDouble("points.minZ2")
                || location.getZ() > config.getDouble("points.maxZ2");
    }

    protected void logInfo(String message) { if (getConfig().getBoolean("logging.enabled", true)) plugin.getLogger().info(message); }
    protected void logEvent(String message) {
        if (getConfig().getBoolean("logging.enabled", true) && getConfig().getBoolean("logging.events", true))
            plugin.getLogger().info("[event] " + message);
    }
}
