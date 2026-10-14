package me.lovelace.loveTweaks.scoreboard;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Order of the flat "Все" view: enabled (scoreboard order) -> available A-Z -> unavailable A-Z. */
public final class PlaceholderSorter {

    /** @param activeIndex index in the player's scoreboard order, -1 when not enabled */
    public record Row(String id, String sortName, int activeIndex, boolean available) {
        boolean active() { return activeIndex >= 0; }
    }

    private PlaceholderSorter() {}

    public static List<String> sort(List<Row> rows) {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("ru"));
        Comparator<Row> byName = Comparator.comparing(Row::sortName, collator).thenComparing(Row::id);

        List<Row> active = new ArrayList<>();
        List<Row> available = new ArrayList<>();
        List<Row> unavailable = new ArrayList<>();
        for (Row r : rows) {
            if (r.active()) active.add(r);
            else if (r.available()) available.add(r);
            else unavailable.add(r);
        }
        active.sort(Comparator.comparingInt(Row::activeIndex));
        available.sort(byName);
        unavailable.sort(byName);

        List<String> out = new ArrayList<>(rows.size());
        for (Row r : active) out.add(r.id());
        for (Row r : available) out.add(r.id());
        for (Row r : unavailable) out.add(r.id());
        return out;
    }
}
