// src/main/java/citadels/util/TSVLoader.java
package citadels.util;

import citadels.model.card.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads /citadels/cards.tsv from classpath.
 * Expected columns: name<TAB>colour<TAB>cost<TAB>qty<TAB>special?
 */
public final class TSVLoader {

    private TSVLoader() {}

    public static List<DistrictCard> loadDistrictDeck() {
        List<DistrictCard> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                TSVLoader.class.getResourceAsStream("/citadels/cards.tsv")))) {

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] t = line.split("\t");
                String name = t[0];
                DistrictColor col = DistrictColor.valueOf(t[1].toUpperCase());
                int cost   = Integer.parseInt(t[2]);
                int qty    = Integer.parseInt(t[3]);
                String sp  = (t.length > 4) ? t[4] : null;

                for (int i = 0; i < qty; i++)
                    list.add(new DistrictCard(name, col, cost, sp));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load cards.tsv", e);
        }
        return list;
    }
}
