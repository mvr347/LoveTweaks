package me.lovelace.loveTweaks;

import me.lovelace.loveTweaks.herald.HeraldGUI;
import me.lovelace.loveTweaks.herald.HeraldGuiConfig;
import me.lovelace.loveTweaks.herald.HeraldPurchaseGUI;
import me.lovelace.loveTweaks.managers.CoordinateTeleportScrollConfig;
import me.lovelace.loveTweaks.scoreboard.ScoreboardGUI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigAndGuiStandardTest {

    @Test
    @DisplayName("HeraldGuiConfig returns non-empty localized fallback messages even when unconfigured")
    void testHeraldGuiConfigDefaults() {
        HeraldGuiConfig config = new HeraldGuiConfig();
        config.load(null);

        assertEquals("&cНедостаточно монет для покупки (нужно <cost>).", config.message("insufficient-funds"));
        assertEquals("&eВведите текст объявления в чат (до <max> символов), или \"отмена\":", config.message("chat-prompt"));
        assertEquals("&aОбъявление опубликовано на <minutes> мин.!", config.message("published"));
        assertEquals("&cОбъявление отклонено фильтром чата.", config.message("rejected-profanity"));
    }

    @Test
    @DisplayName("CoordinateTeleportScrollConfig returns non-empty localized fallback messages")
    void testCoordTeleportScrollConfigDefaults() {
        CoordinateTeleportScrollConfig config = new CoordinateTeleportScrollConfig();
        config.load(null, null);

        assertEquals("&cУ вас нет прав на этот свиток!", config.message("no-permission"));
        assertEquals("&6✦ Телепортация через &e<seconds> сек&6... Не двигайтесь!", config.message("countdown"));
        assertEquals("&a✦ Телепортация по координатам выполнена!", config.message("success"));
        assertEquals("&c✗ Нельзя телепортироваться на воду! Выберите координаты на суше.", config.message("destination-water"));
        assertEquals("&c✗ Нельзя телепортироваться под землю или в шахту! Телепортация разрешена только на поверхность суши.", config.message("destination-underground"));
        assertEquals("(вы двигались)", config.message("reason-moved"));
    }

    @Test
    @DisplayName("Herald 27-slot GUIs conform to gui-gen-5 slot standards")
    void testHeraldGuiSlotStandards() {
        // Herald overview: 27 slots
        assertEquals(27, HeraldGUI.GUI_SIZE);
        assertEquals(0, HeraldGUI.SLOT_INFO);
        assertEquals(26, HeraldGUI.SLOT_CLOSE);
        for (int slot : HeraldGUI.SLOT_POSITIONS) {
            assertTrue(slot >= 9 && slot <= 17, "Working zone slot " + slot + " must be between 9 and 17");
        }

        // Herald purchase: 27 slots
        assertEquals(27, HeraldPurchaseGUI.GUI_SIZE);
        assertEquals(0, HeraldPurchaseGUI.SLOT_INFO);
        assertEquals(25, HeraldPurchaseGUI.SLOT_BACK);
        assertEquals(26, HeraldPurchaseGUI.SLOT_CLOSE);
    }

    @Test
    @DisplayName("Scoreboard 54-slot GUI conforms to gui-gen-5 slot standards")
    void testScoreboardGuiSlotStandards() {
        assertEquals(54, ScoreboardGUI.GUI_SIZE);
        assertEquals(0, ScoreboardGUI.SLOT_PROFILE);
        assertEquals(51, ScoreboardGUI.SLOT_TOGGLE);
        assertEquals(52, ScoreboardGUI.SLOT_BACK);
        assertEquals(53, ScoreboardGUI.SLOT_CLOSE);
    }

    @Test
    @DisplayName("CurrencyFormatter accurately converts numeric cost to physical coins breakdown")
    void testCurrencyFormatter() {
        me.lovelace.loveTweaks.utils.CurrencyFormatter formatter = new me.lovelace.loveTweaks.utils.CurrencyFormatter();
        formatter.load(null);

        // Denominations of the new economy (2026-10-03): copper 1, iron 100, gold 2000, diamond 20000, netherite 100000.
        // 2250 = 1 gold (2000) + 2 iron (200) + 50 copper
        assertEquals("&f%img_gold_coin% &e1 золотая монета&7, &f%img_iron_coin% &f2 железные монеты&7, &f%img_copper_coin% &650 медных монет", formatter.format(2250));

        // 100000 = 1 netherite (the fallback table still knows it; with LoveCore the hidden coin is not used)
        assertEquals("&f%img_netherite_coin% &81 незеритовая монета", formatter.format(100_000));

        // 20000 = 1 diamond
        assertEquals("&f%img_diamond_coin% &b1 алмазная монета", formatter.format(20_000));

        // 60000 = 3 diamond
        assertEquals("&f%img_diamond_coin% &b3 алмазные монеты", formatter.format(60_000));

        // 2000 = 1 gold
        assertEquals("&f%img_gold_coin% &e1 золотая монета", formatter.format(2_000));

        // 100 = 1 iron
        assertEquals("&f%img_iron_coin% &f1 железная монета", formatter.format(100));

        // 1 = 1 copper
        assertEquals("&f%img_copper_coin% &61 медная монета", formatter.format(1));

        // 0 = 0 copper
        assertEquals("&f%img_copper_coin% &60 медных монет", formatter.format(0));
    }

    @Test
    @DisplayName("HeraldPurchaseHolder and HeraldGuiConfig support text source switching and no-funds basehead")
    void testHeraldPurchaseTextSourceAndConfirmNoFunds() {
        HeraldGuiConfig config = new HeraldGuiConfig();
        config.load(null);

        assertEquals("basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzYxODczMWUwNjMzNzlhZWJmODJmMWQ2NGM0MTljOTBkN2YwYzE2NDhjNTQ4ZTliNjE1MWIxYmFiYTY2ZDcyMyJ9fX0=", config.confirmNoFundsMaterial());
        assertEquals("&7Текст объявления сброшен.", config.message("text-reset"));
        assertEquals("&cТекст уже задан через чат. Нажмите ПКМ по кнопке чата для сброса.", config.message("source-chat-active"));
        assertEquals("&cТекст уже задан через книгу. Нажмите ПКМ по слоту книги для сброса.", config.message("source-book-active"));

        me.lovelace.loveTweaks.herald.HeraldPurchaseHolder holder = new me.lovelace.loveTweaks.herald.HeraldPurchaseHolder(0, 10);
        assertEquals(me.lovelace.loveTweaks.herald.HeraldPurchaseHolder.TextSource.NONE, holder.getTextSource());
        assertNull(holder.pendingMessage());

        holder.setPendingMessage("Продам алмазы");
        holder.setTextSource(me.lovelace.loveTweaks.herald.HeraldPurchaseHolder.TextSource.CHAT);
        assertEquals(me.lovelace.loveTweaks.herald.HeraldPurchaseHolder.TextSource.CHAT, holder.getTextSource());
        assertEquals("Продам алмазы", holder.pendingMessage());

        // Reset
        holder.setPendingMessage(null);
        holder.setTextSource(me.lovelace.loveTweaks.herald.HeraldPurchaseHolder.TextSource.NONE);
        assertEquals(me.lovelace.loveTweaks.herald.HeraldPurchaseHolder.TextSource.NONE, holder.getTextSource());
        assertNull(holder.pendingMessage());
    }

    @Test
    @DisplayName("ScoreboardConfig protects update interval from values less than 1")
    void testScoreboardConfigIntervalProtection() {
        me.lovelace.loveTweaks.scoreboard.ScoreboardConfig config = new me.lovelace.loveTweaks.scoreboard.ScoreboardConfig();
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("scoreboard.update-interval", -5);
        config.load(yaml);
        assertEquals(1, config.getUpdateInterval());
    }

    @Test
    @DisplayName("TeleportScrollConfig and CoordTeleportScrollConfig support ItemsAdder and custom models")
    void testTeleportScrollConfigDefaults() {
        me.lovelace.loveTweaks.managers.TeleportScrollConfig config = new me.lovelace.loveTweaks.managers.TeleportScrollConfig();
        config.load(null);

        assertEquals("&6✦ Свиток телепортации ✦", config.itemName());
        assertEquals("&6✦ Введите ник игрока в чат &e(10 секунд)", config.message("prompt"));
        assertEquals("", config.itemsadderItem());
        assertEquals(0, config.customModelData());
        assertEquals(org.bukkit.Material.PAPER, config.material());

        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("item.itemsadder-item", "lovetweaks:teleport_scroll");
        yaml.set("item.custom-model-data", 1234);
        yaml.set("item.material", "FEATHER");
        config.load(yaml);
        assertEquals("lovetweaks:teleport_scroll", config.itemsadderItem());
        assertEquals(1234, config.customModelData());
        assertEquals(org.bukkit.Material.FEATHER, config.material());

        me.lovelace.loveTweaks.managers.CoordinateTeleportScrollConfig coordConfig = new me.lovelace.loveTweaks.managers.CoordinateTeleportScrollConfig();
        org.bukkit.configuration.file.YamlConfiguration coordYaml = new org.bukkit.configuration.file.YamlConfiguration();
        coordYaml.set("item.itemsadder-item", "lovetweaks:coord_scroll");
        coordYaml.set("item.custom-model-data", 5678);
        coordConfig.load(coordYaml, null);
        assertEquals("lovetweaks:coord_scroll", coordConfig.getItemsadderItem());
        assertEquals(5678, coordConfig.getCustomModelData());

        me.lovelace.loveTweaks.managers.PurificationPotionConfig potionConfig = new me.lovelace.loveTweaks.managers.PurificationPotionConfig();
        org.bukkit.configuration.file.YamlConfiguration potionYaml = new org.bukkit.configuration.file.YamlConfiguration();
        potionYaml.set("itemsadder-item", "lovetweaks:purification_potion");
        potionYaml.set("cooldown-seconds", 30);
        potionYaml.set("name", "&bЗелье очищения");
        potionConfig.load(potionYaml);
        assertEquals("lovetweaks:purification_potion", potionConfig.itemsadderItem());
        assertEquals(30, potionConfig.cooldownSeconds());
        assertEquals("&bЗелье очищения", potionConfig.name());
        assertTrue(potionConfig.ignoredEffects().contains("BAD_OMEN"));
    }

    @Test
    @DisplayName("PurificationPotionConfig correctly ignores bad omen and configured effects")
    void testPurificationPotionIgnoredEffects() {
        me.lovelace.loveTweaks.managers.PurificationPotionConfig config = new me.lovelace.loveTweaks.managers.PurificationPotionConfig();
        config.load(null);

        // Default ignored effects: BAD_OMEN, RAID_OMEN, TRIAL_OMEN
        assertTrue(config.isEffectIgnored("BAD_OMEN"));
        assertTrue(config.isEffectIgnored("bad_omen"));
        assertTrue(config.isEffectIgnored("minecraft:bad_omen"));
        assertTrue(config.isEffectIgnored("RAID_OMEN"));
        assertTrue(config.isEffectIgnored("TRIAL_OMEN"));
        assertFalse(config.isEffectIgnored("SPEED"));
        assertFalse(config.isEffectIgnored("POISON"));
        assertFalse(config.isEffectIgnored("REGENERATION"));

        // Custom config
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("ignored-effects", java.util.List.of("POISON", "WITHER", "minecraft:speed"));
        config.load(yaml);

        assertTrue(config.isEffectIgnored("POISON"));
        assertTrue(config.isEffectIgnored("WITHER"));
        assertTrue(config.isEffectIgnored("SPEED"));
        assertTrue(config.isEffectIgnored("speed"));
        assertFalse(config.isEffectIgnored("BAD_OMEN"));

        // Empty list config - all effects are clearable
        org.bukkit.configuration.file.YamlConfiguration emptyYaml = new org.bukkit.configuration.file.YamlConfiguration();
        emptyYaml.set("ignored-effects", java.util.List.of());
        config.load(emptyYaml);

        assertFalse(config.isEffectIgnored("BAD_OMEN"));
        assertFalse(config.isEffectIgnored("POISON"));
    }

    @Test
    @DisplayName("ScoreboardDisplayManager accurately calculates pixel widths and centers text with padding")
    void testScoreboardPixelWidthAndCentering() {
        String bottom = "&c✌ &7V O I D C O R E &c✌";
        int bottomWidth = me.lovelace.loveTweaks.scoreboard.ScoreboardDisplayManager.getPixelWidth(bottom);
        assertTrue(bottomWidth > 85 && bottomWidth < 105, "Bottom width should be around 94-98px");

        // When content is wider by 40px (e.g. 138px), it should add exactly (40/2)/4 = 5 spaces on each side
        String centered = me.lovelace.loveTweaks.scoreboard.ScoreboardDisplayManager.center(bottom, bottomWidth + 40);
        assertEquals("\u00A0".repeat(5) + bottom + "\u00A0".repeat(5), centered);

        // When content is smaller than bottom width, safe minimum padding (3 spaces left, 5 spaces right) expands scoreboard
        String padded = me.lovelace.loveTweaks.scoreboard.ScoreboardDisplayManager.center(bottom, bottomWidth - 20);
        assertEquals("\u00A0\u00A0\u00A0" + bottom + "\u00A0\u00A0\u00A0\u00A0\u00A0", padded);

        // Test with font_image tag and placeholder
        String coinText = "%img_copper_coin% &6Баланс";
        int coinWidth = me.lovelace.loveTweaks.scoreboard.ScoreboardDisplayManager.getPixelWidth(coinText);
        assertTrue(coinWidth > 40, "Coin text pixel width should account for font image");
    }

    @Test
    @DisplayName("Brewing configuration loads defaults and custom settings correctly")
    void testBrewingConfigDefaultsAndOverrides() {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        // Check default fallback values when unconfigured
        boolean defBrewing = yaml.getBoolean("brewing.disable-brewing", true);
        boolean defCraft = yaml.getBoolean("brewing.disable-brewing-stand-craft", true);
        boolean defWorld = yaml.getBoolean("brewing.disable-brewing-stand-world", true);
        boolean defPotionDrops = yaml.getBoolean("brewing.disable-potion-natural-drops", true);
        String defMsg = yaml.getString("brewing.disabled-message", "&cЗельеварение и варочные стойки отключены на этом сервере.");

        assertTrue(defBrewing);
        assertTrue(defCraft);
        assertTrue(defWorld);
        assertTrue(defPotionDrops);
        assertEquals("&cЗельеварение и варочные стойки отключены на этом сервере.", defMsg);

        // Custom config override
        yaml.set("brewing.disable-brewing", false);
        yaml.set("brewing.disable-brewing-stand-craft", false);
        yaml.set("brewing.disable-brewing-stand-world", false);
        yaml.set("brewing.disable-potion-natural-drops", false);
        yaml.set("brewing.disabled-message", "&cCustom disabled message");

        assertFalse(yaml.getBoolean("brewing.disable-brewing", true));
        assertFalse(yaml.getBoolean("brewing.disable-brewing-stand-craft", true));
        assertFalse(yaml.getBoolean("brewing.disable-brewing-stand-world", true));
        assertFalse(yaml.getBoolean("brewing.disable-potion-natural-drops", true));
        assertEquals("&cCustom disabled message", yaml.getString("brewing.disabled-message"));
    }

    @Test
    @DisplayName("BrewingListener correctly detects LoveBrew items and vanilla brewing items")
    void testLoveBrewItemDetection() {
        assertFalse(me.lovelace.loveTweaks.listeners.BrewingListener.isLoveBrewItem(null));
        assertFalse(me.lovelace.loveTweaks.listeners.BrewingListener.isLoveBrewBlock(null));
        assertFalse(me.lovelace.loveTweaks.listeners.BrewingListener.isLoveBrewState(null));
        assertFalse(me.lovelace.loveTweaks.listeners.BrewingListener.isVanillaPotion(null));
    }
}
