package ru.ruinfall;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.ruinfall.acidrain.AcidRainController;
import ru.ruinfall.bedbombs.BedExplosionListener;
import ru.ruinfall.eventmobs.EventMobController;
import ru.ruinfall.herobrine.HerobrineRitualManager;
import ru.ruinfall.items.CustomArsenalController;
import ru.ruinfall.particles.ParticleShieldController;
import ru.ruinfall.radiation.RadiationZoneController;

import java.util.List;

public final class RuinfallSuitePlugin extends JavaPlugin {
    private AcidRainController acidRain;
    private ParticleShieldController particleShields;
    private BedExplosionListener bedBombs;
    private HerobrineRitualManager herobrine;
    private CustomArsenalController customArsenal;
    private RadiationZoneController radiationZones;
    private EventMobController eventMobs;
    private List<RuinfallFeature> features;

    @Override
    public void onEnable() {
        acidRain = new AcidRainController(this);
        particleShields = new ParticleShieldController(this);
        bedBombs = new BedExplosionListener(this);
        herobrine = new HerobrineRitualManager(this);
        customArsenal = new CustomArsenalController(this);
        radiationZones = new RadiationZoneController(this);
        eventMobs = new EventMobController(this);

        features = List.of(acidRain, particleShields, bedBombs, herobrine, customArsenal, radiationZones, eventMobs);

        getServer().getPluginManager().registerEvents(acidRain, this);
        getServer().getPluginManager().registerEvents(bedBombs, this);
        getServer().getPluginManager().registerEvents(herobrine, this);
        getServer().getPluginManager().registerEvents(customArsenal, this);
        getServer().getPluginManager().registerEvents(radiationZones, this);
        getServer().getPluginManager().registerEvents(eventMobs, this);

        registerExecutor("aweather", acidRain);
        registerExecutor("giveitems", customArsenal);
        registerExecutor("startritual", herobrine);
        registerExecutor("ediff", eventMobs);
        registerTabCompleter("aweather", acidRain);
        registerTabCompleter("ediff", eventMobs);
        registerExecutor("particleshield", (sender, command, label, args) -> {
            if (!sender.hasPermission("ruinfall.particles.admin")) return true;
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                particleShields.reloadShields();
                sender.sendMessage("§aконфиг щитов перезагружен.");
                getLogger().info("[particle-shields.yml] [event] " + sender.getName() + " перезагрузил конфиг.");
                return true;
            }
            return false;
        });

        getLogger().info("Ruinfall Suite загружает 7 подсистем...");
        for (RuinfallFeature feature : features) feature.enable();
        getLogger().info("Ruinfall Suite успешно запущен. единый JAR активен.");
    }

    @Override
    public void onDisable() {
        if (features != null) {
            for (int i = features.size() - 1; i >= 0; i--) features.get(i).disable();
        }
        getLogger().info("Ruinfall Suite отключён. все 7 подсистем остановлены.");
    }

    private void registerExecutor(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) command.setExecutor(executor);
        else getLogger().warning("Команда не найдена в plugin.yml: /" + name);
    }

    private void registerTabCompleter(String name, org.bukkit.command.TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command != null) command.setTabCompleter(completer);
    }
}
