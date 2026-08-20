package ru.ruinfall;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public abstract class RuinfallFeature {
    protected final JavaPlugin plugin;
    private final String configFileName;
    protected FileConfiguration config;

    protected RuinfallFeature(JavaPlugin plugin, String configFileName) {
        this.plugin = plugin;
        this.configFileName = configFileName;
        this.config = loadConfig();
    }

    protected FileConfiguration getConfig() {
        return config;
    }

    protected void reloadFeatureConfig() {
        this.config = loadConfig();
    }

    private FileConfiguration loadConfig() {
        File configDir = new File(plugin.getDataFolder(), "configs");
        if (!configDir.exists()) configDir.mkdirs();
        File file = new File(configDir, configFileName);
        if (!file.exists()) {
            plugin.saveResource("configs/" + configFileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    protected void logInfo(String message) {
        if (config.getBoolean("logging.enabled", true)) plugin.getLogger().info("[" + configFileName + "] " + message);
    }

    protected void logEvent(String message) {
        if (config.getBoolean("logging.enabled", true) && config.getBoolean("logging.events", true)) {
            plugin.getLogger().info("[" + configFileName + "] [event] " + message);
        }
    }

    public abstract void enable();
    public abstract void disable();
}
