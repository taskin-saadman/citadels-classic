// src/main/java/citadels/model/game/ScoreCalculator.java
package citadels.model.game;

import citadels.model.card.DistrictCard;
import citadels.model.card.DistrictColor;
import citadels.model.player.Player;

import java.util.*;

/**
 * Static helper that awards points exactly per assignment spec
 * and classic-edition rulebook (Dragon Gate + University = +2 each).
 */
public final class ScoreCalculator {

    private ScoreCalculator() {}

    public static Map<Player, Integer> compute(List<Player> players,
                                               int firstCompletedSeat) {

        Map<Player, Integer> map = new LinkedHashMap<>();
        for (Player p : players) map.put(p, 0);

        /* — 1. district costs — */
        for (Player p : players) {
            int base = p.getCity().stream().mapToInt(DistrictCard::getCost).sum();
            map.compute(p, (k, v) -> v + base);
        }

        /* — 2. colour diversity bonus — */
        for (Player p : players) {
            boolean allFive = EnumSet.allOf(DistrictColor.class).stream()
                    .allMatch(col -> p.getCity().stream()
                                      .anyMatch(c -> c.getColor() == col));
            if (allFive) map.compute(p, (k, v) -> v + 3);
        }

        /* — 3. completion bonuses — */
        for (Player p : players) {
            if (p.getCity().size() >= 8) {
                int bonus = (p.getId() == firstCompletedSeat) ? 4 : 2;
                map.compute(p, (k, v) -> v + bonus);
            }
        }

        /* — 4. purple unique extras — */
        for (Player p : players) {
            int extra = 0;
            for (DistrictCard d : p.getCity()) {
                String n = d.getName().toLowerCase(Locale.ROOT);
                if (n.contains("dragon gate") || n.contains("university")) extra += 2;
            }
            map.compute(p, (k, v) -> v + extra);
        }
        return map;
    }

    /** Returns the winner(s) with highest score. */
    public static List<Player> winners(Map<Player, Integer> scores) {
        int max = scores.values().stream().max(Integer::compare).orElse(0);
        return scores.entrySet().stream()
                     .filter(e -> e.getValue() == max)
                     .map(Map.Entry::getKey)
                     .toList();
    }
}
