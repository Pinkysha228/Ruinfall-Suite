package ru.ruinfall.herobrine;

import ru.ruinfall.RuinfallFeature;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class HerobrineRitualManager extends RuinfallFeature implements Listener, CommandExecutor {
    public HerobrineRitualManager(org.bukkit.plugin.java.JavaPlugin plugin) {
        super(plugin, "herobrine-ritual.yml");
    }

    private final Map<UUID, Location> victims = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private File dataFile;
    private FileConfiguration dataConfig;
    private long secondsPassed;
    private long lastWeekReset;

    @Override
    public void enable() {
        initDataFile();
        secondsPassed = dataConfig.getLong("seconds-passed", 0);
        lastWeekReset = dataConfig.getLong("last-week-reset", System.currentTimeMillis());
        Bukkit.getScheduler().runTaskTimer(plugin, this::mainTick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::playHeartbeat, 0L, 10L);
        logInfo("запущен. ритуалы готовы.");
    }

    @Override
    public void disable() {
        saveState();
        victims.clear();
        logInfo("отключён. данные сохранены.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        Player target = args.length > 0 ? Bukkit.getPlayer(args[0])
                : Bukkit.getOnlinePlayers().stream().findAny().orElse(null);
        if (args.length > 0 && target == null) {
            sender.sendMessage("§cигрок не найден.");
            return true;
        }
        if (target == null) {
            sender.sendMessage("§6некого пугать.");
            return true;
        }
        sender.sendMessage("§cритуал запущен для " + target.getName());
        logEvent("ручной ритуал для " + target.getName() + " от " + sender.getName());
        startSequence(target);
        return true;
    }

    private void mainTick() {
        secondsPassed++;
        if (System.currentTimeMillis() - lastWeekReset > 604_800_000L) {
            dataConfig.set("players", null);
            lastWeekReset = System.currentTimeMillis();
            saveState();
            logEvent("сброшены недельные лимиты.");
        }

        long interval = getConfig().getLong("check-interval-minutes", 480) * 60;
        if (secondsPassed >= interval) {
            secondsPassed = 0;
            tryTriggerEvent();
            saveState();
        }
    }

    private void tryTriggerEvent() {
        if (new Random().nextInt(100) >= getConfig().getInt("trigger-chance", 5)) return;
        List<Player> candidates = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (candidates.isEmpty()) return;
        Collections.shuffle(candidates);
        int maxPerWeek = getConfig().getInt("max-per-week", 3);
        for (Player player : candidates) {
            int count = dataConfig.getInt("players." + player.getUniqueId(), 0);
            if (count < maxPerWeek) {
                startSequence(player);
                dataConfig.set("players." + player.getUniqueId(), count + 1);
                logEvent("автоматический ритуал выбран для " + player.getName());
                break;
            }
        }
    }

    private void startSequence(Player player) {
        int darkDur = getConfig().getInt("effects.darkness-duration", 40);
        int teleDelay = getConfig().getInt("effects.teleport-delay", 10);
        int poisonDur = getConfig().getInt("effects.poison-duration", 30);
        int killDelay = getConfig().getInt("effects.kill-delay", 30);

        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, darkDur * 20, 0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World end = findEndWorld();
            if (end == null || !player.isOnline()) return;

            spawnWaterBottle();
            victims.put(player.getUniqueId(), player.getLocation());
            player.teleport(getEndLocation(end));
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonDur * 20, 4));

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (victims.containsKey(player.getUniqueId()) && player.isOnline()) {
                    player.setHealth(0);
                    victims.remove(player.getUniqueId());
                    logEvent("ритуал завершён смертью игрока " + player.getName());
                }
            }, killDelay * 20L);
        }, teleDelay * 20L);
    }

    private void spawnWaterBottle() {
        String worldName = getConfig().getString("storage.world", "world_the_end");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Block block = world.getBlockAt(getConfig().getInt("storage.x"), getConfig().getInt("storage.y"),
                getConfig().getInt("storage.z"));
        if (!(block.getState() instanceof Container container)) return;

        Inventory inventory = container.getInventory();
        int slot = getConfig().getInt("storage.slot", 13);
        ItemStack current = inventory.getItem(slot);
        if (current != null && current.getType() != Material.AIR) return;

        ItemStack water = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) water.getItemMeta();
        if (meta != null) {
            meta.setBasePotionType(PotionType.WATER);
            meta.displayName(miniMessage.deserialize("<aqua>Святая вода</aqua>"));
            water.setItemMeta(meta);
        }
        inventory.setItem(slot, water);
    }

    private Location getEndLocation(World world) {
        return new Location(world, getConfig().getDouble("end-coords.x"), getConfig().getDouble("end-coords.y"),
                getConfig().getDouble("end-coords.z"), (float) getConfig().getDouble("end-coords.yaw"),
                (float) getConfig().getDouble("end-coords.pitch"));
    }

    private void playHeartbeat() {
        World end = findEndWorld();
        if (end == null) return;
        Location location = getEndLocation(end);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(end) && player.getLocation().distance(location) <= 50) {
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.2f, 0.8f);
            }
        }
    }

    @EventHandler
    public void onDrink(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item.getType() != Material.POTION || !(item.getItemMeta() instanceof PotionMeta meta)
                || meta.getBasePotionType() != PotionType.WATER) return;

        Player player = event.getPlayer();
        World end = findEndWorld();
        if (end == null || !player.getWorld().equals(end)) return;

        Location endLocation = getEndLocation(end);
        if (player.getLocation().distance(endLocation) > 50) return;

        Location back = victims.remove(player.getUniqueId());
        if (back == null) return;

        player.teleport(back);
        player.removePotionEffect(PotionEffectType.POISON);
        player.removePotionEffect(PotionEffectType.DARKNESS);
        String message = getConfig().getString("wake-up.message", "<yellow>это был кошмар...</yellow>");
        player.sendMessage(miniMessage.deserialize(message));
        back.getBlock().setType(Material.WATER, true);
        logEvent(player.getName() + " проснулся с помощью святой воды.");
    }

    private World findEndWorld() {
        return Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.THE_END)
                .findFirst().orElseGet(() -> Bukkit.getWorld("world_the_end"));
    }

    private void initDataFile() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            logInfo("не удалось создать папку данных.");
        }
        dataFile = new File(plugin.getDataFolder(), "herobrine-data.yml");
        if (!dataFile.exists()) {
            try { if (!dataFile.createNewFile()) logInfo("herobrine-data.yml уже существует."); }
            catch (IOException exception) { plugin.getLogger().severe("не удалось создать herobrine-data.yml: " + exception.getMessage()); }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveState() {
        if (dataConfig == null || dataFile == null) return;
        dataConfig.set("seconds-passed", secondsPassed);
        dataConfig.set("last-week-reset", lastWeekReset);
        try { dataConfig.save(dataFile); }
        catch (IOException exception) { plugin.getLogger().severe("не удалось сохранить herobrine-data.yml: " + exception.getMessage()); }
    }

    protected void logInfo(String message) { if (getConfig().getBoolean("logging.enabled", true)) plugin.getLogger().info(message); }
    protected void logEvent(String message) {
        if (getConfig().getBoolean("logging.enabled", true) && getConfig().getBoolean("logging.events", true))
            plugin.getLogger().info("[event] " + message);
    }
}
