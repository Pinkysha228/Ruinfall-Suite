package ru.ruinfall.acidrain;

import ru.ruinfall.RuinfallFeature;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class AcidRainController extends RuinfallFeature implements CommandExecutor, TabCompleter, Listener {
    public AcidRainController(org.bukkit.plugin.java.JavaPlugin plugin) {
        super(plugin, "acid-rain.yml");
    }

    private final Map<UUID, Integer> rainTypes = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void enable() {

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                if (!world.hasStorm()) continue;
                int type = rainTypes.getOrDefault(world.getUID(), 0);
                if (type == 0) continue;
                for (Player player : world.getPlayers()) {
                    if (isUnderRain(player)) applyEffects(player, type);
                }
            }
        }, 20L, 20L);

        logInfo("запущен. кислотный дождь активирован.");
    }

    @Override
    public void disable() {
        rainTypes.clear();
        logInfo("отключён. состояния дождя очищены.");
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        World world = event.getWorld();
        if (event.toWeatherState()) {
            int type = rainTypes.computeIfAbsent(world.getUID(), ignored -> rollRainType());
            logEvent("в мире " + world.getName() + " начался дождь, тип=" + type);
            broadcastRainStart(world, type);
        } else {
            rainTypes.remove(world.getUID());
            logEvent("в мире " + world.getName() + " дождь закончился.");
        }
    }

    private int rollRainType() {
        int chance = ThreadLocalRandom.current().nextInt(100);
        int hardChance = getConfig().getInt("chances.hard", 5);
        int mediumChance = getConfig().getInt("chances.medium", 15);
        if (chance < hardChance) return 2;
        if (chance < hardChance + mediumChance) return 1;
        return 0;
    }

    private void broadcastRainStart(World world, int type) {
        if (!getConfig().getBoolean("enabled", true)) return;
        String path = switch (type) {
            case 1 -> "messages.start_medium";
            case 2 -> "messages.start_hard";
            default -> "messages.start_normal";
        };
        for (String line : getConfig().getStringList(path)) {
            for (Player player : world.getPlayers()) {
                player.sendMessage(miniMessage.deserialize(line));
            }
        }
    }

    private boolean isUnderRain(Player player) {
        var location = player.getLocation();
        return location.getY() >= player.getWorld().getHighestBlockYAt(location);
    }

    private void applyEffects(Player player, int type) {
        if (type == 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0, true, true));
        } else if (type == 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 4, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 0, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 2, true, true));
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ruinfall.acidrain.admin")) {
            sender.sendMessage("§cнет прав.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadFeatureConfig();
            sender.sendMessage("§aконфиг кислотного дождя перезагружен.");
            logEvent("администратор " + sender.getName() + " перезагрузил конфиг.");
            return true;
        }
        if (args.length < 1) return false;

        World world = sender instanceof Player player
                ? player.getWorld()
                : Bukkit.getWorlds().stream().findFirst().orElse(null);
        if (world == null) return true;

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "clear" -> {
                rainTypes.remove(world.getUID());
                world.setStorm(false);
            }
            case "normal" -> { rainTypes.put(world.getUID(), 0); world.setStorm(true); }
            case "medium" -> { rainTypes.put(world.getUID(), 1); world.setStorm(true); }
            case "hard" -> { rainTypes.put(world.getUID(), 2); world.setStorm(true); }
            default -> { return false; }
        }
        logEvent(sender.getName() + " установил режим " + args[0] + " в мире " + world.getName());
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 ? List.of("normal", "medium", "hard", "clear", "reload") : Collections.emptyList();
    }

    protected void logInfo(String message) {
        if (getConfig().getBoolean("logging.enabled", true)) plugin.getLogger().info(message);
    }

    protected void logEvent(String message) {
        if (getConfig().getBoolean("logging.enabled", true) && getConfig().getBoolean("logging.events", true)) {
            plugin.getLogger().info("[event] " + message);
        }
    }
}
