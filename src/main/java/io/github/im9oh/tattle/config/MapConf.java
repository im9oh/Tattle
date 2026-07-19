package io.github.im9oh.tattle.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Conf backed by a plain nested Map (as produced by a YAML parser).
 * Used by the simulator so config.yml can be loaded without Bukkit.
 */
public final class MapConf implements Conf {

    private final Map<?, ?> root;

    public MapConf(Map<?, ?> root) {
        this.root = root == null ? Map.of() : root;
    }

    private Object node(String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    @Override
    public String getString(String path, String def) {
        Object value = node(path);
        return value instanceof String s ? s : (value != null ? String.valueOf(value) : def);
    }

    @Override
    public int getInt(String path, int def) {
        return node(path) instanceof Number n ? n.intValue() : def;
    }

    @Override
    public double getDouble(String path, double def) {
        return node(path) instanceof Number n ? n.doubleValue() : def;
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return node(path) instanceof Boolean b ? b : def;
    }

    @Override
    public List<Map<?, ?>> getMapList(String path) {
        List<Map<?, ?>> result = new ArrayList<>();
        if (node(path) instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    result.add(map);
                }
            }
        }
        return result;
    }

    @Override
    public Set<String> keys(String path) {
        Set<String> keys = new LinkedHashSet<>();
        if (node(path) instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                keys.add(String.valueOf(key));
            }
        }
        return keys;
    }
}
