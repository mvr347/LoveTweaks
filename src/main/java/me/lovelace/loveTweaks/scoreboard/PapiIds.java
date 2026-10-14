package me.lovelace.loveTweaks.scoreboard;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts PlaceholderAPI expansion identifiers ({@code %id_params%} -> {@code id}) from text. */
public final class PapiIds {

    private static final Pattern TOKEN = Pattern.compile("%([A-Za-z0-9]+)_[^%]*%");

    private PapiIds() {}

    public static Set<String> extract(String text) {
        Set<String> ids = new LinkedHashSet<>();
        if (text == null) return ids;
        Matcher m = TOKEN.matcher(text);
        while (m.find()) ids.add(m.group(1).toLowerCase());
        return ids;
    }
}
