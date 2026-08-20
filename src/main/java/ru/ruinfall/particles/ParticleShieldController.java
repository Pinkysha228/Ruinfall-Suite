package ru.ruinfall.particles;

import ru.ruinfall.RuinfallFeature;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class ParticleShieldController extends RuinfallFeature {
    public ParticleShieldController(org.bukkit.plugin.java.JavaPlugin plugin) {
        super(plugin, "particle-shields.yml");
    }

    private final List<CachedShield> shields = new ArrayList<>();

    @Override
    public void enable() {
        loadShields();
        startTask();
        logInfo("запущен. загружено щитов: " + shields.size());
    }

    @Override
    public void disable() {
        shields.clear();
        logInfo("отключён. кэш щитов очищен.");
    }

    public void reloadShields() {
        loadShields();
    }

    private void loadShields() {
        shields.clear();
        reloadFeatureConfig();
        ConfigurationSection section = getConfig().getConfigurationSection("points");
        if (section == null) {
            logInfo("секция points отсутствует.");
            return;
        }

        for (String key : section.getKeys(false)) {
            String path = "points." + key + ".";
            World world = Bukkit.getWorld(getConfig().getString(path + "world", "world"));
            if (world == null) {
                logInfo("точка " + key + " пропущена: мир не найден.");
                continue;
            }

            Location center = new Location(world,
                    getConfig().getDouble(path + "x"),
                    getConfig().getDouble(path + "y"),
                    getConfig().getDouble(path + "z"));
            double radius = getConfig().getDouble(path + "radius", 100.0);
            int totalPoints = Math.max(2, getConfig().getInt(path + "density", 10000));
            Particle particle;
            try {
                particle = Particle.valueOf(getConfig().getString(path + "particle", "END_ROD").toUpperCase());
            } catch (IllegalArgumentException exception) {
                logInfo("точка " + key + " пропущена: неизвестная частица.");
                continue;
            }

            int frequency = Math.max(1, getConfig().getInt(path + "frequency", 5));
            boolean explode = getConfig().getBoolean(path + "explode", false);
            double explodeDist = getConfig().getDouble(path + "explode-distance", 0.1);

            List<Vector> points = new ArrayList<>(totalPoints);
            double phi = Math.PI * (3.0 - Math.sqrt(5.0));
            for (int i = 0; i < totalPoints; i++) {
                double y = 1 - (i / (double) (totalPoints - 1)) * 2;
                double radiusAtY = Math.sqrt(1 - y * y);
                double theta = phi * i;
                points.add(new Vector(Math.cos(theta) * radiusAtY, y, Math.sin(theta) * radiusAtY).multiply(radius));
            }
            shields.add(new CachedShield(center, points, particle, frequency, explode, explodeDist));
        }
        logEvent("конфиг загружен, щитов: " + shields.size());
    }

    private record CachedShield(Location center, List<Vector> offsets, Particle particle,
                                int frequency, boolean explode, double explodeDist) {}

    private void startTask() {
        new BukkitRunnable() {
            long ticks;
            @Override
            public void run() {
                ticks++;
                for (CachedShield shield : shields) {
                    if (ticks % shield.frequency() != 0) continue;
                    World world = shield.center().getWorld();
                    if (world == null || world.getPlayers().isEmpty()) continue;
                    boolean nearby = world.getPlayers().stream()
                            .anyMatch(player -> player.getLocation().distanceSquared(shield.center()) < 1_000_000);
                    if (!nearby) continue;

                    double cx = shield.center().getX();
                    double cy = shield.center().getY();
                    double cz = shield.center().getZ();

                    for (Vector offset : shield.offsets()) {
                        if (shield.explode()) {
                            Vector direction = offset.clone().normalize();
                            world.spawnParticle(shield.particle(), cx + offset.getX(), cy + offset.getY(),
                                    cz + offset.getZ(), 0, direction.getX(), direction.getY(), direction.getZ(),
                                    shield.explodeDist(), null, true);
                        } else {
                            world.spawnParticle(shield.particle(), cx + offset.getX(), cy + offset.getY(),
                                    cz + offset.getZ(), 1, 0, 0, 0, 0, null, true);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    protected void logInfo(String message) {
        if (getConfig().getBoolean("logging.enabled", true)) plugin.getLogger().info(message);
    }
    protected void logEvent(String message) {
        if (getConfig().getBoolean("logging.enabled", true) && getConfig().getBoolean("logging.events", true))
            plugin.getLogger().info("[event] " + message);
    }
}
