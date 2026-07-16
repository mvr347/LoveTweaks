package me.lovelace.loveTweaks.scoreboard;

import me.lovelace.loveTweaks.LoveTweaks;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScoreboardDataManager {

    private final LoveTweaks plugin;
    private File dataFile;
    private YamlConfiguration data;
    private final Map<UUID, PlayerScoreboardState> cache = new HashMap<>();

    public ScoreboardDataManager(LoveTweaks plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        dataFile = new File(plugin.getDataFolder(), "scoreboard_data.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        cache.clear();
    }

    public PlayerScoreboardState getState(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadFromFile);
    }

    private PlayerScoreboardState loadFromFile(UUID uuid) {
        String key = uuid.toString();
        if (!data.contains(key)) {
            List<String> defaults = new ArrayList<>(plugin.getScoreboardConfig().getSectionOrder());
            return new PlayerScoreboardState(true, defaults);
        }
        boolean enabled = data.getBoolean(key + ".enabled", true);
        List<String> sections = data.getStringList(key + ".sections");
        // Remove any section IDs that no longer exist in config
        List<String> validSections = new ArrayList<>(sections);
        List<String> known = plugin.getScoreboardConfig().getSectionOrder();
        validSections.retainAll(known);
        return new PlayerScoreboardState(enabled, validSections);
    }

    public void savePlayer(UUID uuid) {
        PlayerScoreboardState state = cache.get(uuid);
        if (state == null) return;
        String key = uuid.toString();
        data.set(key + ".enabled", state.isScoreboardEnabled());
        data.set(key + ".sections", state.getActiveSections());
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save scoreboard data for " + uuid + ": " + e.getMessage());
        }
    }

    public void saveAll() {
        for (UUID uuid : new ArrayList<>(cache.keySet())) {
            savePlayer(uuid);
        }
    }

    public void unload(UUID uuid) {
        savePlayer(uuid);
        cache.remove(uuid);
    }
}
