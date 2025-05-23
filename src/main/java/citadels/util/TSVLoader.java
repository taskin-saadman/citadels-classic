// src/main/java/citadels/util/TSVLoader.java
package citadels.util;

import citadels.model.card.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Loads cards.tsv (Name&nbsp;Qty&nbsp;color&nbsp;cost&nbsp;text).
 */
public final class TSVLoader {

    /**
     * Loads cards.tsv (Name&nbsp;Qty&nbsp;color&nbsp;cost&nbsp;text).
     * The main game deck is loaded here.
     * @return List<DistrictCard>
     */
    public static List<DistrictCard> loadDistrictDeck() {
        /* locate resource on classpath */
        InputStream in = TSVLoader.class.getResourceAsStream("/citadels/cards.tsv");
        if (in == null) in = TSVLoader.class.getResourceAsStream("/cards.tsv");
        if (in == null) throw new RuntimeException("cards.tsv not found");

        List<DistrictCard> deck = new ArrayList<>();

        //try to read the file (utf-8 encoding)
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) { // until end of file
                line = line.trim(); //trim whitespace
                if (line.isEmpty()) continue; //skip empty lines
                // Split on:  (one-or-more tabs)  OR  (two-or-more spaces)
                String[] t = line.split("\\t"); //regex to split on tabs
                if (t.length < 4) continue;     // need at least 4 columns

                if (t[0].equalsIgnoreCase("name")) continue; //skip header row

                String name = t[0].trim(); //name

                int qty, cost;
                try {
                    qty  = Integer.parseInt(t[1].trim());
                    cost = Integer.parseInt(t[3].trim());
                } catch (NumberFormatException ex) {
                    continue; //malformed numeric columns → skip row
                }

                String colorRaw = t[2].trim().toUpperCase(Locale.ROOT);
                DistrictColor color;
                try {
                    color = DistrictColor.valueOf(colorRaw);
                } catch (IllegalArgumentException ex) {
                    continue;    // invalid colour → skip row
                }

                //text of purple cards
                String special = null;
                if (t.length > 4) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 4; i < t.length; i++) {
                        if (i > 4) sb.append(' ');
                        sb.append(t[i].trim());
                    }
                    special = sb.toString();
                }

                //add quantity copies
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
