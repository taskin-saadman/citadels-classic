// src/main/java/citadels/util/TSVLoader.java
package citadels.util;

import citadels.model.card.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Robust loader for the assignment-supplied cards.tsv (Name&nbsp;Qty&nbsp;color&nbsp;cost&nbsp;text).
 * Splits on either one-or-more tabs OR two-or-more spaces, so multi-word names stay intact.
 */
public final class TSVLoader {

    private TSVLoader() { }

    public static List<DistrictCard> loadDistrictDeck() {

        /* locate resource on classpath */
        InputStream in = TSVLoader.class.getResourceAsStream("/citadels/cards.tsv");
        if (in == null) in = TSVLoader.class.getResourceAsStream("/cards.tsv");
        if (in == null) throw new RuntimeException("cards.tsv not found");

        List<DistrictCard> deck = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                // Split on:  (one-or-more tabs)  OR  (two-or-more spaces)
                String[] t = line.split("\\t+| {2,}");
                if (t.length < 4) continue;           // need at least 4 columns

                /* Skip header row (first token "name" case-insensitive) */
                if (t[0].equalsIgnoreCase("name")) continue;

                /* Expected order: Name  Qty  color  cost  [special ...] */
                String name = t[0].trim();

                int qty, cost;
                try {
                    qty  = Integer.parseInt(t[1].trim());
                    cost = Integer.parseInt(t[3].trim());
                } catch (NumberFormatException ex) {
                    /* malformed numeric columns → skip row */
                    continue;
                }

                String colorRaw = t[2].trim().toUpperCase(Locale.ROOT);
                DistrictColor color;
                try {
                    color = DistrictColor.valueOf(colorRaw);
                } catch (IllegalArgumentException ex) {
                    continue;    // invalid colour → skip row
                }

                /* Join remaining tokens (index ≥4) into special text */
                String special = null;
                if (t.length > 4) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 4; i < t.length; i++) {
                        if (i > 4) sb.append(' ');
                        sb.append(t[i].trim());
                    }
                    special = sb.toString();
                }

                /* Add quantity copies */
                for (int i = 0; i < qty; i++)
                    deck.add(new DistrictCard(name, color, cost, special));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load cards.tsv", e);
        }

        if (deck.isEmpty())
            throw new RuntimeException("cards.tsv loaded but no valid cards found");

        return deck;
    }
}
