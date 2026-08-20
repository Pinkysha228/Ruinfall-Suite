package ru.ruinfall.eventmobs;

import ru.ruinfall.RuinfallFeature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class EventMobController extends RuinfallFeature implements Listener, CommandExecutor, TabCompleter {
    public EventMobController(org.bukkit.plugin.java.JavaPlugin plugin) {
        super(plugin, "event-mobs.yml");
    }

    private NamespacedKey bossKey;
    private File dataFile;
    private FileConfiguration dataConfig;
    private long difficultyIncreaseSeconds;
    private long waveIntervalSeconds;
    private int bossPotionChance;

    @Override
    public void enable() {
        bossKey = new NamespacedKey(plugin, "event_boss");
        loadSettings();
        createDataConfig();

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                int newLevel = getLevel(player.getUniqueId()) + 1;
                setLevel(player.getUniqueId(), newLevel);
                broadcastToAdmins(Component.text("[EventMobs] " + player.getName() + " -> " + newLevel, NamedTextColor.GRAY));
            }
        }, difficultyIncreaseSeconds * 20L, difficultyIncreaseSeconds * 20L);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (players.isEmpty()) return;
            Collections.shuffle(players);
            spawnWave(players.getFirst());
        }, waveIntervalSeconds * 20L, waveIntervalSeconds * 20L);

        logInfo("запущен. волны и прогрессия активны.");
    }

    @Override
    public void disable() {
        saveData();
        logInfo("отключён. прогресс сохранён.");
    }

    private void loadSettings() {
        reloadFeatureConfig();
        difficultyIncreaseSeconds = Math.max(1, getConfig().getLong("difficulty-increase-seconds", 3600));
        waveIntervalSeconds = Math.max(1, getConfig().getLong("wave-interval-seconds", 600));
        bossPotionChance = Math.max(0, Math.min(100, getConfig().getInt("boss-potion-chance", 5)));
    }

    private void createDataConfig() {
        dataFile = new File(plugin.getDataFolder(), "event-mobs-data.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try { dataFile.createNewFile(); }
            catch (IOException exception) { plugin.getLogger().severe("не удалось создать event-mobs-data.yml: " + exception.getMessage()); }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private int getLevel(UUID uuid) { return dataConfig.getInt("levels." + uuid, 0); }

    private void setLevel(UUID uuid, int level) {
        dataConfig.set("levels." + uuid, level);
        try { dataConfig.save(dataFile); }
        catch (IOException exception) { plugin.getLogger().severe("не удалось сохранить event-mobs-data.yml: " + exception.getMessage()); }
    }

    private void saveData() {
        if (dataConfig == null || dataFile == null) return;
        try { dataConfig.save(dataFile); }
        catch (IOException exception) { plugin.getLogger().severe("не удалось сохранить event-mobs-data.yml: " + exception.getMessage()); }
    }

    private void broadcastToAdmins(Component message) {
        Bukkit.getConsoleSender().sendMessage(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("ruinfall.eventmobs.admin")) player.sendMessage(message);
        }
    }

    private void spawnWave(Player player) {
        int level = getLevel(player.getUniqueId());
        Location location = findSpawnLocation(player);
        broadcastToAdmins(Component.text("[EventMobs] волна (" + level + ") атакует " + player.getName(), NamedTextColor.DARK_RED));

        if (level >= 1) spawnUnit(EntityType.ZOMBIE, 2, location, level, false);
        if (level >= 4) spawnUnit(EntityType.SKELETON, 1, location, level, false);
        if (level >= 6) spawnUnit(EntityType.ZOMBIE, 1, location, level, true);
        if (level >= 10) spawnUnit(EntityType.SKELETON, 1, location, level, true);
        if (level >= 15) spawnUnit(EntityType.VINDICATOR, 5, location, level, false);
        if (level >= 30) spawnUnit(EntityType.WITHER_SKELETON, 3, location, level, false);
        if (level >= 50) spawnBoss(location);
        logEvent("волна уровня " + level + " создана для " + player.getName());
    }

    private void spawnUnit(EntityType type, int count, Location location, int difficulty, boolean special) {
        for (int i = 0; i < count; i++) {
            LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, type);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, -1, 0, false, false));

            AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                double base = health.getBaseValue();
                double target = Math.max(base, base * difficulty * 0.02);
                health.setBaseValue(target);
                entity.setHealth(target);
            }

            if (difficulty >= 10) entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, -1, 1));
            if (difficulty >= 30) entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, -1, 0));
            if (special) entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, -1, 1));
        }
    }

    private void spawnBoss(Location location) {
        WitherSkeleton boss = (WitherSkeleton) location.getWorld().spawnEntity(location, EntityType.WITHER_SKELETON);
        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
        boss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, -1, 0, false, false));

        AttributeInstance health = boss.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) { health.setBaseValue(200); boss.setHealth(200); }
        boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, -1, 2));
        equip(boss, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS, 4);

        EntityEquipment equipment = boss.getEquipment();
        if (equipment != null) {
            ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
            sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
            equipment.setItemInMainHand(sword);
        }
        logEvent("создан босс-скелет.");
    }

    private void equip(LivingEntity entity, Material helmet, Material chestplate, Material leggings, Material boots, int protection) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(enchant(helmet, protection));
            equipment.setChestplate(enchant(chestplate, protection));
            equipment.setLeggings(enchant(leggings, protection));
            equipment.setBoots(enchant(boots, protection));
        }
    }

    private ItemStack enchant(Material material, int level) {
        ItemStack item = new ItemStack(material);
        item.addUnsafeEnchantment(Enchantment.PROTECTION, level);
        return item;
    }

    private Location findSpawnLocation(Player player) {
        Location origin = player.getLocation();
        for (int i = 0; i < 20; i++) {
            double x = ThreadLocalRandom.current().nextDouble(-10, 10);
            double z = ThreadLocalRandom.current().nextDouble(-10, 10);
            double y = ThreadLocalRandom.current().nextDouble(-2, 3);
            Location check = origin.clone().add(x, y, z);
            if (check.getBlock().isPassable() && check.clone().add(0, -1, 0).getBlock().getType().isSolid()) return check;
        }
        return origin;
    }

    @EventHandler
    public void onBossHurt(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof WitherSkeleton boss)
                || !boss.getPersistentDataContainer().has(bossKey, PersistentDataType.BYTE)) return;

        if (ThreadLocalRandom.current().nextInt(100) < bossPotionChance) {
            Location potionLocation = boss.getLocation().add(0, 1.0, 0);
            ThrownPotion potion = (ThrownPotion) boss.getWorld().spawnEntity(potionLocation, EntityType.POTION);
            ItemStack item = new ItemStack(Material.SPLASH_POTION);
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta != null) {
                meta.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 1), true);
                meta.setColor(Color.MAROON);
                item.setItemMeta(meta);
                potion.setItem(item);
                potion.setVelocity(new Vector(0, -0.5, 0));
            }
            logEvent("босс бросил атакующее зелье.");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ruinfall.eventmobs.admin")) return true;
        if (args.length < 2) return false;

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cигрок не найден.");
            return true;
        }

        if (args[0].equalsIgnoreCase("set") && args.length == 3) {
            try {
                int level = Integer.parseInt(args[2]);
                setLevel(target.getUniqueId(), level);
                sender.sendMessage("§aуровень обновлён.");
                logEvent(sender.getName() + " установил уровень " + level + " игроку " + target.getName());
            } catch (NumberFormatException exception) {
                sender.sendMessage("§cуровень должен быть числом.");
            }
        } else if (args[0].equalsIgnoreCase("spawn")) {
            spawnWave(target);
        } else if (args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            sender.sendMessage("§aконфиг перезагружен.");
            logEvent(sender.getName() + " перезагрузил конфиг.");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("ruinfall.eventmobs.admin")) return Collections.emptyList();
        if (args.length == 1) return List.of("set", "spawn", "reload");
        return Collections.emptyList();
    }

    protected void logInfo(String message) { if (getConfig().getBoolean("logging.enabled", true)) plugin.getLogger().info(message); }
    protected void logEvent(String message) {
        if (getConfig().getBoolean("logging.enabled", true) && getConfig().getBoolean("logging.events", true))
            plugin.getLogger().info("[event] " + message);
    }
}
