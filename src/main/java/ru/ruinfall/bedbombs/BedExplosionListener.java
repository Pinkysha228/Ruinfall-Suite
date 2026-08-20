package ru.ruinfall.bedbombs;

import ru.ruinfall.RuinfallFeature;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;

public final class BedExplosionListener extends RuinfallFeature implements Listener {
    public BedExplosionListener(org.bukkit.plugin.java.JavaPlugin plugin) {
        super(plugin, "bed-bombs.yml");
    }

    @Override
    public void enable() {
        logInfo("запущен. режим взрыва кровати: " + getConfig().getBoolean("enabled", false));
    }

    @Override
    public void disable() {
        logInfo("отключён.");
    }

    @EventHandler
    public void onPlayerSleep(PlayerBedEnterEvent event) {
        if (!getConfig().getBoolean("enabled", false)) return;
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;

        var player = event.getPlayer();
        Location location = event.getBed().getLocation();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.wakeup(false);
            float power = (float) getConfig().getDouble("explosion.power", 10.0);
            boolean fire = getConfig().getBoolean("explosion.set-fire", true);
            boolean breakBlocks = getConfig().getBoolean("explosion.break-blocks", true);
            logEvent("взрыв кровати для " + player.getName() + " в " + location);
            if (location.getWorld() != null) {
                location.getWorld().createExplosion(location, power, fire, breakBlocks, player);
            }
            player.setHealth(0.0);
        }, 1L);
    }

    protected void logInfo(String message) { if (getConfig().getBoolean("logging.enabled", true)) plugin.getLogger().info(message); }
    protected void logEvent(String message) {
        if (getConfig().getBoolean("logging.enabled", true) && getConfig().getBoolean("logging.events", true))
            plugin.getLogger().info("[event] " + message);
    }
}
