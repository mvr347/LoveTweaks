package me.lovelace.loveTweaks.herald;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All Herald GUI text — titles, item name/lore, player-facing messages — loaded from
 * {@code herald.gui.*}. Mirrors {@code ScoreboardConfig}'s split: a dedicated config holder
 * per menu instead of piling every string getter onto {@link me.lovelace.loveTweaks.LoveTweaksConfig}.
 */
public final class HeraldGuiConfig {

    private String overviewTitle = "&6Королевский Глашатай";
    private String purchaseTitle = "&6Новое объявление";

    private String infoName = "&6Голос Королевства";
    private List<String> infoLore = List.of();

    private String slotFreeName = "&aСлот свободен";
    private List<String> slotFreeLore = List.of();

    private String slotOccupiedName = "&7Слот занят";
    private List<String> slotOccupiedLore = List.of();

    private String bookHintName = "&eВложите подписанную книгу";
    private List<String> bookHintLore = List.of();

    private String writeChatName = "&bНаписать в чат";
    private List<String> writeChatLore = List.of();

    private String durationName = "&eДлительность";
    private List<String> durationLore = List.of();

    private String confirmName = "&aОпубликовать объявление";
    private List<String> confirmLore = List.of();

    private String confirmDisabledName = "&8Опубликовать объявление";
    private List<String> confirmDisabledLore = List.of();

    private final Map<String, String> messages = new HashMap<>();

    public void load(ConfigurationSection section) {
        if (section == null) return;

        overviewTitle = section.getString("overview-title", overviewTitle);
        purchaseTitle = section.getString("purchase-title", purchaseTitle);

        infoName = section.getString("info.name", infoName);
        infoLore = stringList(section, "info.lore");

        slotFreeName = section.getString("slot-free.name", slotFreeName);
        slotFreeLore = stringList(section, "slot-free.lore");

        slotOccupiedName = section.getString("slot-occupied.name", slotOccupiedName);
        slotOccupiedLore = stringList(section, "slot-occupied.lore");

        bookHintName = section.getString("book-hint.name", bookHintName);
        bookHintLore = stringList(section, "book-hint.lore");

        writeChatName = section.getString("write-chat-button.name", writeChatName);
        writeChatLore = stringList(section, "write-chat-button.lore");

        durationName = section.getString("duration-button.name", durationName);
        durationLore = stringList(section, "duration-button.lore");

        confirmName = section.getString("confirm-button.name", confirmName);
        confirmLore = stringList(section, "confirm-button.lore");

        confirmDisabledName = section.getString("confirm-disabled.name", confirmDisabledName);
        confirmDisabledLore = stringList(section, "confirm-disabled.lore");

        messages.clear();
        ConfigurationSection messagesSection = section.getConfigurationSection("messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                messages.put(key, messagesSection.getString(key, ""));
            }
        }
    }

    private static List<String> stringList(ConfigurationSection section, String path) {
        List<String> list = section.getStringList(path);
        return list.isEmpty() ? new ArrayList<>() : list;
    }

    public String overviewTitle() { return overviewTitle; }
    public String purchaseTitle() { return purchaseTitle; }
    public String infoName() { return infoName; }
    public List<String> infoLore() { return infoLore; }
    public String slotFreeName() { return slotFreeName; }
    public List<String> slotFreeLore() { return slotFreeLore; }
    public String slotOccupiedName() { return slotOccupiedName; }
    public List<String> slotOccupiedLore() { return slotOccupiedLore; }
    public String bookHintName() { return bookHintName; }
    public List<String> bookHintLore() { return bookHintLore; }
    public String writeChatName() { return writeChatName; }
    public List<String> writeChatLore() { return writeChatLore; }
    public String durationName() { return durationName; }
    public List<String> durationLore() { return durationLore; }
    public String confirmName() { return confirmName; }
    public List<String> confirmLore() { return confirmLore; }
    public String confirmDisabledName() { return confirmDisabledName; }
    public List<String> confirmDisabledLore() { return confirmDisabledLore; }

    /** Raw message template for {@code key} (with its own {@code <placeholder>} tags), or the key itself if unset. */
    public String message(String key) {
        return messages.getOrDefault(key, key);
    }
}
