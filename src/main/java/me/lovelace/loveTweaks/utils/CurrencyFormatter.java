package me.lovelace.loveTweaks.utils;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Конвертер числовой стоимости в текстовое представление физической валюты
 * (монет).
 * Например: 75 -> "1 золотая монета, 2 железные монеты, 5 медных монет".
 */
public class CurrencyFormatter {

    public record Coin(String id, long value, String one, String few, String many) {
    }

    private final List<Coin> coins = new ArrayList<>();
    private String separator = "&7, ";

    public CurrencyFormatter() {
        initDefaults();
    }

    private void initDefaults() {
        coins.clear();
        coins.add(new Coin("netherite_coin", 100_000, "&f%img_netherite_coin% &8<amount> незеритовая монета", "&f%img_netherite_coin% &8<amount> незеритовые монеты",
                "&f%img_netherite_coin% &8<amount> незеритовых монет"));
        coins.add(new Coin("diamond_coin", 20_000, "&f%img_diamond_coin% &b<amount> алмазная монета", "&f%img_diamond_coin% &b<amount> алмазные монеты",
                "&f%img_diamond_coin% &b<amount> алмазных монет"));
        coins.add(new Coin("gold_coin", 2_000, "&f%img_gold_coin% &e<amount> золотая монета", "&f%img_gold_coin% &e<amount> золотые монеты",
                "&f%img_gold_coin% &e<amount> золотых монет"));
        coins.add(new Coin("iron_coin", 100, "&f%img_iron_coin% &f<amount> железная монета", "&f%img_iron_coin% &f<amount> железные монеты",
                "&f%img_iron_coin% &f<amount> железных монет"));
        coins.add(new Coin("copper_coin", 1, "&f%img_copper_coin% &6<amount> медная монета", "&f%img_copper_coin% &6<amount> медные монеты",
                "&f%img_copper_coin% &6<amount> медных монет"));
        sortCoins();
    }

    public void load(ConfigurationSection section) {
        initDefaults();
        if (section == null) {
            return;
        }

        separator = section.getString("separator", separator);

        ConfigurationSection coinsSection = section.getConfigurationSection("coins");
        if (coinsSection != null) {
            List<Coin> customCoins = new ArrayList<>();
            for (String key : coinsSection.getKeys(false)) {
                ConfigurationSection cSec = coinsSection.getConfigurationSection(key);
                if (cSec == null)
                    continue;
                long value = cSec.getLong("value", 1);
                String one = cSec.getString("one", cSec.getString("name", "&f<amount> " + key));
                String few = cSec.getString("few", cSec.getString("name-few", one));
                String many = cSec.getString("many", cSec.getString("name-many", few));
                customCoins.add(new Coin(key, value, one, few, many));
            }
            if (!customCoins.isEmpty()) {
                coins.clear();
                coins.addAll(customCoins);
                sortCoins();
            }
        }
    }

    private void sortCoins() {
        coins.sort(Comparator.comparingLong(Coin::value).reversed());
    }

    /**
     * Coins used for splitting an amount. With LoveCore present the denominations (ids and values) come
     * from LoveEconomy, the single source of truth: hidden ones (netherite) are not used, and the texts come
     * from this config by coin id. 2026-10-03: the {@code value} keys of the config are only a fallback for a
     * server without LoveCore - they used to be the second copy that drifted from LoveCore.
     */
    private List<Coin> effectiveCoins() {
        java.util.List<dev.lovelace.lovecore.api.economy.Denomination> live;
        try {
            live = dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.economy.LoveEconomy.class)
                    .map(dev.lovelace.lovecore.api.economy.LoveEconomy::denominations).orElse(null);
        } catch (Throwable t) {
            live = null; // LoveCore API absent: use the configured coins
        }
        if (live == null || live.isEmpty()) {
            return coins;
        }
        List<Coin> result = new ArrayList<>();
        for (dev.lovelace.lovecore.api.economy.Denomination den : live) {
            String id = den.itemId();
            int colon = id.indexOf(':');
            String shortId = colon >= 0 ? id.substring(colon + 1) : id;
            Coin configured = null;
            for (Coin coin : coins) {
                if (coin.id().equalsIgnoreCase(shortId)) {
                    configured = coin;
                    break;
                }
            }
            String generic = "&f%img_" + shortId + "% &f<amount>";
            result.add(configured != null
                    ? new Coin(shortId, den.value(), configured.one(), configured.few(), configured.many())
                    : new Coin(shortId, den.value(), generic, generic, generic));
        }
        result.sort(Comparator.comparingLong(Coin::value).reversed());
        return result;
    }

    public String format(long totalAmount) {
        List<Coin> coins = effectiveCoins();
        if (totalAmount <= 0) {
            Coin lowest = coins.isEmpty() ? null : coins.get(coins.size() - 1);
            if (lowest != null) {
                return pluralize(0, lowest.one(), lowest.few(), lowest.many());
            }
            return "0 монет";
        }

        long remaining = totalAmount;
        List<String> parts = new ArrayList<>();
        for (Coin coin : coins) {
            if (coin.value() <= 0)
                continue;
            long count = remaining / coin.value();
            if (count > 0) {
                parts.add(pluralize(count, coin.one(), coin.few(), coin.many()));
                remaining %= coin.value();
            }
        }

        if (parts.isEmpty()) {
            return String.valueOf(totalAmount);
        }
        return String.join(separator, parts);
    }

    public static String pluralize(long amount, String one, String few, String many) {
        long rem100 = Math.abs(amount) % 100;
        long rem10 = Math.abs(amount) % 10;
        String pattern;
        if (rem100 >= 11 && rem100 <= 19) {
            pattern = many;
        } else if (rem10 == 1) {
            pattern = one;
        } else if (rem10 >= 2 && rem10 <= 4) {
            pattern = few;
        } else {
            pattern = many;
        }
        return pattern.replace("<amount>", String.valueOf(amount));
    }
}
