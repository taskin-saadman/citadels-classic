// src/main/java/citadels/model/game/ScoreCalculator.java
package citadels.model.game;

import citadels.model.card.DistrictCard;
import citadels.model.card.DistrictColor;
import citadels.model.player.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Calculates final scores for the Citadels classic edition.
 */
public final class ScoreCalculator {

    private ScoreCalculator() { }

    public static Map<Player, Integer> compute(List<Player> players,
                                               int firstCompletedSeat) {

        Map<Player, Integer> score = new LinkedHashMap<>();
        for (Player p : players) score.put(p, 0);

        /* — 1. raw district cost — */
        for (Player p : players) {
            int base = p.getCity().stream()
                         .mapToInt(DistrictCard::getCost).sum();
            score.put(p, score.get(p) + base);
        }

        /* — 2. colour diversity bonus (3 pts) — */
        for (Player p : players) {

            EnumSet<DistrictColor> colours = EnumSet.noneOf(DistrictColor.class);
            boolean hasSchool   = false;
            boolean hasHaunted  = false;

            for (DistrictCard d : p.getCity()) {
                colours.add(d.getColor());
                if (d.isSchoolOfMagic())   hasSchool   = true;
                if (d.isHauntedQuarter())  hasHaunted  = true;
            }

            if (hasSchool)  colours.addAll(EnumSet.allOf(DistrictColor.class));
            if (hasHaunted) colours.addAll(EnumSet.allOf(DistrictColor.class));

            if (colours.size() == 5)
                score.put(p, score.get(p) + 3);
        }

        /* — 3. completion bonuses — */
        for (Player p : players) {
            if (p.getCity().size() >= 8) {
                int bonus = (p.getId() == firstCompletedSeat) ? 4 : 2;
                score.put(p, score.get(p) + bonus);
            }
        }

        /* — 4. purple unique extras (+2) — */
        for (Player p : players) {
            int extra = 0;
            for (DistrictCard d : p.getCity())
                if (d.givesExtraPoints()) extra += 2;
            score.put(p, score.get(p) + extra);
        }
        return score;
    }

    /** Returns list of player(s) tied for the highest score. */
    public static List<Player> winners(Map<Player, Integer> scores) {
        int max = scores.values().stream()
                        .max(Integer::compare).orElse(0);
        return scores.entrySet().stream()
                     .filter(e -> e.getValue() == max)
                     .map(Map.Entry::getKey)
                     .collect(Collectors.toList());
    }
}
