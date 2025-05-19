package citadels.model.player;

import citadels.model.card.*;
import citadels.model.game.CitadelsGame;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Heuristic-based AI for computer-controlled players.
 * <p>Assumptions: methods in CitadelsGame already enforce all rule
 * constraints (bishop immunity, build-limit, warlord cost, etc.).</p>
 */
public final class AIPlayer extends Player {

    private static final Random RNG = new Random();

    public AIPlayer(int id) {
        super(id);
    }

    @Override
    public void takeTurn(CitadelsGame game) {

        /* ---------- 0. Execute mandatory / early ability ---------- */
        switch (character.getRank()) {
            case 1 -> assassinTurn(game);    // Assassin
            case 2 -> thiefTurn(game);       // Thief
        }

        /* ---------- 1. Gather resources -------------------------- */
        gatherResources(game);

        /* ---------- 2. Architect extra draw ---------------------- */
        if (character.getRank() == 7)   // Architect
            game.drawCards(this, 2);

        /* ---------- 3. Build districts --------------------------- */
        buildPhase(game);

        /* ---------- 4. Late abilities ---------------------------- */
        switch (character.getRank()) {
            case 3 -> magicianPostBuild(game);  // Magician
            case 8 -> warlordTurn(game);        // Warlord
        }
    }

    /* =============================================================
       RESOURCE CHOICE
       =========================================================== */

    private void gatherResources(CitadelsGame game) {
        boolean canAffordSomething = hand.stream()
                .anyMatch(c -> c.getCost() <= gold);

        // heuristic: if broke or cannot build, prefer gold; else take cards if hand small
        if (!canAffordSomething && gold < 2) {
            game.collectGold(this);
        } else if (hand.size() <= 2) {
            game.drawCards(this, 2);
        } else {
            game.collectGold(this);
        }
    }

    /* =============================================================
       BUILD PHASE
       =========================================================== */

    private void buildPhase(CitadelsGame game) {

        // try to build up to limit each turn (Architect may be 3)
        int limit = getBuildLimit();
        int built = 0;

        while (built < limit) {

            Optional<DistrictCard> best = hand.stream()
                    .filter(c -> c.getCost() <= gold && !cityContains(c.getName()))
                    .sorted(buildComparator())
                    .findFirst();

            if (best.isEmpty()) break;
            game.buildDistrict(this, best.get());
            built++;
        }
    }

    private static Comparator<DistrictCard> buildComparator() {
        return Comparator.<DistrictCard>comparingInt(DistrictCard::getCost).reversed()
                .thenComparing(c -> c.getColor() == DistrictColor.PURPLE ? 0 : 1) // prefer purple
                .thenComparing(DistrictCard::getName);
    }

    /* =============================================================
       CHARACTER POWERS
       =========================================================== */

    /* ---------- Assassin (kill) ---------- */
    private void assassinTurn(CitadelsGame game) {
        Player target = richestOpponent(game, this);
        int victimRank = (target == null || target.getCharacter() == null)
                ? RNG.nextInt(7) + 2   // random 2-8
                : target.getCharacter().getRank();
        game.killCharacter(victimRank);
    }

    /* ---------- Thief (rob) -------------- */
    private void thiefTurn(CitadelsGame game) {
        Player target = richestOpponent(game, this);
        int victimRank = (target == null || target.getCharacter() == null)
                ? RNG.nextInt(6) + 3   // random 3-8
                : target.getCharacter().getRank();
        game.setRobTarget(this, victimRank);
    }

    /* ---------- Magician ----------------- */
    private void magicianPostBuild(CitadelsGame game) {
        // if another player has ≥2 more cards, swap; else redraw duplicates
        Player richestHand = game.getPlayers().stream()
                .filter(p -> p != this)
                .max(Comparator.comparingInt(p -> p.getHand().size()))
                .orElse(null);

        if (richestHand != null && richestHand.getHand().size() >= hand.size() + 2) {
            game.swapHands(this, richestHand);
        } else {
            // discard duplicates or high-cost unbuildable
            List<Integer> discIdx = new ArrayList<>();
            for (int i = 0; i < hand.size(); i++) {
                DistrictCard d = hand.get(i);
                boolean duplicate = cityContains(d.getName());
                boolean tooExpensive = d.getCost() > gold + 2;
                if (duplicate || tooExpensive) discIdx.add(i);
            }
            if (!discIdx.isEmpty()) {
                // simple: discard all, draw same amount
                discIdx.sort(Comparator.reverseOrder());
                discIdx.forEach(i -> hand.remove((int) i));
                game.drawCards(this, discIdx.size());
            }
        }
    }

    /* ---------- Warlord ------------------ */
    private void warlordTurn(CitadelsGame game) {
        // choose cheapest destroyable district of a vulnerable player
        Player victim = game.getPlayers().stream()
                .filter(p -> p != this && p.getCity().size() < 8)
                .filter(p -> !game.bishopProtected().contains(p))
                .min(Comparator.comparingInt(p ->
                        p.getCity().stream().mapToInt(DistrictCard::getCost).min().orElse(99)))
                .orElse(null);

        if (victim == null) return;

        int idx = -1;
        int minCost = Integer.MAX_VALUE;
        List<DistrictCard> vc = victim.getCity();
        for (int i = 0; i < vc.size(); i++) {
            int c = vc.get(i).getCost();
            int trueCost = Math.max(0, c - 1);
            if (trueCost <= gold && trueCost < minCost) { idx = i; minCost = trueCost; }
        }
        if (idx >= 0) game.destroyDistrict(this, victim, idx);
    }

    /* =============================================================
       Helper utilities
       =========================================================== */

    private static Player richestOpponent(CitadelsGame g, Player self) {
        return g.getPlayers().stream()
                .filter(p -> p != self)
                .max(Comparator.comparingInt(Player::getGold))
                .orElse(null);
    }
}
