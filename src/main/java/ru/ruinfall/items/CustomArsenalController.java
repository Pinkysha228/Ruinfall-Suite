package ru.ruinfall.items;

import ru.ruinfall.RuinfallFeature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

public final class CustomArsenalController extends RuinfallFeature implements Listener, CommandExecutor {
    public CustomArsenalController(org.bukkit.plugin.java.JavaPlugin plugin) {
        super(plugin, "custom-arsenal.yml");
    }

    private NamespacedKey thunderBowKey;
    private NamespacedKey poisonBladeKey;

    @Override
    public void enable() {
        thunderBowKey = new NamespacedKey(plugin, "thunder_bow");
        poisonBladeKey = new NamespacedKey(plugin, "poison_blade");

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                ItemStack boots = player.getInventory().getBoots();
                if (boots == null || !boots.hasItemMeta()) continue;
                ItemMeta meta = boots.getItemMeta();
                if (meta.hasDisplayName() && meta.getDisplayName().contains("Ботинки пушинки")) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false, true));
                }
            }
        }, 0L, 10L);

        logInfo("запущен. арсенал зарегистрирован.");
    }

    @Override
    public void disable() {
        logInfo("отключён.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cкоманда доступна только игроку.");
            return true;
        }

        ItemStack sociopathBow = createBase(Material.BOW, "Лук социопата", true);
        sociopathBow.editMeta(meta -> {
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.addEnchant(Enchantment.PUNCH, 5, true);
        });

        ItemStack thunderBow = createBase(Material.BOW, "Лук Богини Гроз", true);
        thunderBow.editMeta(meta -> meta.getPersistentDataContainer().set(thunderBowKey, PersistentDataType.BYTE, (byte) 1));

        ItemStack chestplate = createBase(Material.NETHERITE_CHESTPLATE, "Незеритовый нагрудник", true);
        chestplate.editMeta(meta -> { if (meta instanceof ArmorMeta armor) armor.setGlider(true); });

        ItemStack unbreakableSword = createBase(Material.NETHERITE_SWORD, "Меч Несокрушимого", true);
        unbreakableSword.editMeta(meta -> {
            if (meta instanceof Repairable repairable) repairable.setRepairCost(1_000_000);
            meta.addEnchant(Enchantment.SHARPNESS, 10, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
            meta.addEnchant(Enchantment.LOOTING, 3, true);
            meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
        });

        ItemStack pickaxe = createBase(Material.NETHERITE_PICKAXE, "Кирка шахтера", true);
        pickaxe.editMeta(meta -> {
            meta.addEnchant(Enchantment.EFFICIENCY, 10, true);
            meta.addEnchant(Enchantment.UNBREAKING, 5, true);
            meta.addEnchant(Enchantment.FORTUNE, 5, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
        });

        ItemStack leggings = createBase(Material.NETHERITE_LEGGINGS, "Штаны ниндзя", true);
        leggings.editMeta(meta -> meta.addEnchant(Enchantment.SWIFT_SNEAK, 5, true));

        ItemStack poisonBlade = createBase(Material.GOLDEN_SWORD, "Клинок ниндзя", true);
        poisonBlade.editMeta(meta -> {
            meta.displayName(meta.displayName().color(NamedTextColor.GOLD));
            if (meta instanceof Repairable repairable) repairable.setRepairCost(1_000_000);
            meta.addEnchant(Enchantment.UNBREAKING, 10, true);
            meta.getPersistentDataContainer().set(poisonBladeKey, PersistentDataType.BYTE, (byte) 1);
        });

        ItemStack boots = createBase(Material.NETHERITE_BOOTS, "Ботинки пушинки", true);
        boots.editMeta(meta -> {
            meta.addEnchant(Enchantment.FEATHER_FALLING, 10, true);
            meta.addEnchant(Enchantment.PROTECTION, 4, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.addEnchant(Enchantment.SOUL_SPEED, 3, true);
            meta.addEnchant(Enchantment.DEPTH_STRIDER, 3, true);
        });

        player.getInventory().addItem(sociopathBow, thunderBow, chestplate, unbreakableSword, pickaxe, leggings, poisonBlade, boots);
        player.sendMessage(Component.text("забирай шмот.").color(NamedTextColor.LIGHT_PURPLE));
        logEvent("игрок " + player.getName() + " получил набор.");
        return true;
    }

    private ItemStack createBase(Material material, String name, boolean epic) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
            if (epic) meta.setRarity(ItemRarity.EPIC);
        });
        return item;
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (event.getBow() == null || !event.getBow().hasItemMeta()) return;
        if (!event.getBow().getItemMeta().getPersistentDataContainer().has(thunderBowKey, PersistentDataType.BYTE)) return;
        if (!(event.getProjectile() instanceof Arrow arrow)) return;

        arrow.getPersistentDataContainer().set(thunderBowKey, PersistentDataType.BYTE, (byte) 1);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (arrow.isDead() || arrow.isOnGround()) { cancel(); return; }
                arrow.getWorld().spawnParticle(Particle.END_ROD, arrow.getLocation(), 1, 0, 0, 0, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)
                || !arrow.getPersistentDataContainer().has(thunderBowKey, PersistentDataType.BYTE)) return;

        arrow.getWorld().strikeLightning(arrow.getLocation()).setFireTicks(0);
        if (event.getHitEntity() instanceof LivingEntity victim) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
        }
        logEvent("громовой лук сработал.");
    }

    @EventHandler
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity victim)) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!hand.hasItemMeta() || !hand.getItemMeta().getPersistentDataContainer().has(poisonBladeKey, PersistentDataType.BYTE)) return;
        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 9));
        logEvent(player.getName() + " применил клинок ниндзя.");
    }

    protected void logInfo(String message) { if (getConfig().getBoolean("logging.enabled", true)) plugin.getLogger().info(message); }
    protected void logEvent(String message) {
        if (getConfig().getBoolean("logging.enabled", true) && getConfig().getBoolean("logging.events", true))
            plugin.getLogger().info("[event] " + message);
    }
}
