package io.github.im9oh.tattle.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only view over a config tree, addressed by dotted paths.
 * Lets {@link Settings} load from Bukkit's FileConfiguration on a server
 * ({@link BukkitConf}) or from a plain YAML map in the simulator ({@link MapConf}).
 */
public interface Conf {

    String getString(String path, String def);

    int getInt(String path, int def);

    double getDouble(String path, double def);

    boolean getBoolean(String path, boolean def);

    List<Map<?, ?>> getMapList(String path);

    /** Immediate child keys of the section at path; empty if absent or not a section. */
    Set<String> keys(String path);
}
