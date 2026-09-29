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

    private String bookActiveName = "&aТекст из книги";
    private List<String> bookActiveLore = List.of();

    private String bookDisabledName = "&8Вложить книгу";
    private List<String> bookDisabledLore = List.of();

    private String writeChatName = "&bНаписать в чат";
    private List<String> writeChatLore = List.of();

    private String writeChatActiveName = "&aТекст из чата";
    private List<String> writeChatActiveLore = List.of();

    private String writeChatDisabledName = "&8Написать в чат";
    private List<String> writeChatDisabledLore = List.of();

    private String durationName = "&eДлительность";
    private List<String> durationLore = List.of();

    private String confirmName = "&aОпубликовать объявление";
    private List<String> confirmLore = List.of();

    private String confirmDisabledName = "&8Опубликовать объявление";
    private List<String> confirmDisabledLore = List.of();

    private String confirmNoFundsName = "&cОпубликовать объявление";
    private String confirmNoFundsMaterial = "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzYxODczMWUwNjMzNzlhZWJmODJmMWQ2NGM0MTljOTBkN2YwYzE2NDhjNTQ4ZTliNjE1MWIxYmFiYTY2ZDcyMyJ9fX0=";
    private List<String> confirmNoFundsLore = List.of();

    private static final Map<String, String> DEFAULT_MESSAGES = Map.ofEntries(
            Map.entry("insufficient-funds", "&cНедостаточно монет для покупки (нужно <cost>)."),
            Map.entry("too-long", "&cСлишком длинное сообщение (максимум <max> символов, у вас <length>)."),
            Map.entry("empty-message", "&cОбъявление не может быть пустым."),
            Map.entry("rejected-profanity", "&cОбъявление отклонено фильтром чата."),
            Map.entry("published", "&aОбъявление опубликовано на <minutes> мин.!"),
            Map.entry("chat-prompt", "&eВведите текст объявления в чат (до <max> символов), или \"отмена\":"),
            Map.entry("chat-cancelled", "&7Ввод объявления отменён."),
            Map.entry("text-reset", "&7Текст объявления сброшен."),
            Map.entry("source-chat-active", "&cТекст уже задан через чат. Нажмите ПКМ по кнопке чата для сброса."),
            Map.entry("source-book-active", "&cТекст уже задан через книгу. Нажмите ПКМ по слоту книги для сброса."),
            Map.entry("no-permission", "&cУ вас нет прав для этой команды."),
            Map.entry("wrong-book", "&cСюда можно вложить только подписанную книгу."),
            Map.entry("no-free-slots", "&cСейчас все слоты Глашатая заняты."),
            Map.entry("broadcast-format", "&6&l[Голос Королевства] &f<buyer>&7: &e<message>"),
            // Server-declared hunt announcement (LoveBehavior -> LoveHunt auto-bounty bridge),
            // not one of the player-purchased slots above.
            Map.entry("hunt-announcement-format", "&6&l[Глашатай] &7Внимание! Игрок &c<player> &7замечен на сервере — на его голову объявлена &4охота&7!"),
            // Daily contract rotation (LoveContracts), see HeraldManager#announceContractsRotation.
            Map.entry("contracts-rotation-format", "&6[Глашатай] &eВнимание! Новые контракты доступны на доске объявлений!")
    );

    private final Map<String, String> messages = new HashMap<>();
    private final me.lovelace.loveTweaks.utils.CurrencyFormatter currencyFormatter = new me.lovelace.loveTweaks.utils.CurrencyFormatter();

    public void load(ConfigurationSection section) {
        messages.clear();
        messages.putAll(DEFAULT_MESSAGES);
        if (section == null) {
            currencyFormatter.load(null);
            return;
        }

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

        bookActiveName = section.getString("book-active.name", bookActiveName);
        bookActiveLore = stringList(section, "book-active.lore");

        bookDisabledName = section.getString("book-disabled.name", bookDisabledName);
        bookDisabledLore = stringList(section, "book-disabled.lore");

        writeChatName = section.getString("write-chat-button.name", writeChatName);
        writeChatLore = stringList(section, "write-chat-button.lore");

        writeChatActiveName = section.getString("write-chat-active.name", writeChatActiveName);
        writeChatActiveLore = stringList(section, "write-chat-active.lore");

        writeChatDisabledName = section.getString("write-chat-disabled.name", writeChatDisabledName);
        writeChatDisabledLore = stringList(section, "write-chat-disabled.lore");

        durationName = section.getString("duration-button.name", durationName);
        durationLore = stringList(section, "duration-button.lore");

        confirmName = section.getString("confirm-button.name", confirmName);
        confirmLore = stringList(section, "confirm-button.lore");

        confirmDisabledName = section.getString("confirm-disabled.name", confirmDisabledName);
        confirmDisabledLore = stringList(section, "confirm-disabled.lore");

        confirmNoFundsName = section.getString("confirm-no-funds.name", confirmNoFundsName);
        confirmNoFundsMaterial = section.getString("confirm-no-funds.material", confirmNoFundsMaterial);
        confirmNoFundsLore = stringList(section, "confirm-no-funds.lore");

        currencyFormatter.load(section.getConfigurationSection("currency"));

        ConfigurationSection messagesSection = section.getConfigurationSection("messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                messages.put(key, messagesSection.getString(key, DEFAULT_MESSAGES.getOrDefault(key, "")));
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
    public String bookActiveName() { return bookActiveName; }
    public List<String> bookActiveLore() { return bookActiveLore; }
    public String bookDisabledName() { return bookDisabledName; }
    public List<String> bookDisabledLore() { return bookDisabledLore; }
    public String writeChatName() { return writeChatName; }
    public List<String> writeChatLore() { return writeChatLore; }
    public String writeChatActiveName() { return writeChatActiveName; }
    public List<String> writeChatActiveLore() { return writeChatActiveLore; }
    public String writeChatDisabledName() { return writeChatDisabledName; }
    public List<String> writeChatDisabledLore() { return writeChatDisabledLore; }
    public String durationName() { return durationName; }
    public List<String> durationLore() { return durationLore; }
    public String confirmName() { return confirmName; }
    public List<String> confirmLore() { return confirmLore; }
    public String confirmDisabledName() { return confirmDisabledName; }
    public List<String> confirmDisabledLore() { return confirmDisabledLore; }
    public String confirmNoFundsName() { return confirmNoFundsName; }
    public String confirmNoFundsMaterial() { return confirmNoFundsMaterial; }
    public List<String> confirmNoFundsLore() { return confirmNoFundsLore; }
    public me.lovelace.loveTweaks.utils.CurrencyFormatter getCurrencyFormatter() { return currencyFormatter; }
    public String formatCost(long cost) { return currencyFormatter.format(cost); }

    /** Raw message template for {@code key} (with its own {@code <placeholder>} tags), or the default if unset. */
    public String message(String key) {
        return messages.getOrDefault(key, DEFAULT_MESSAGES.getOrDefault(key, key));
    }
}
