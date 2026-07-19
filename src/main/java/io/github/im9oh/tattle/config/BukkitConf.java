package io.github.im9oh.tattle.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Conf backed by Bukkit's FileConfiguration; used on a live server. */
public final class BukkitConf implements Conf {

    private final FileConfiguration config;

    public BukkitConf(FileConfiguration config) {
        this.config = config;
    }

    @Override
    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    @Override
    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    @Override
    public double getDouble(String path, double def) {
        return config.getDouble(path, def);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    @Override
    public List<Map<?, ?>> getMapList(String path) {
        return config.getMapList(path);
    }

    @Override
    public Set<String> keys(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section == null ? Set.of() : section.getKeys(false);
    }
}
