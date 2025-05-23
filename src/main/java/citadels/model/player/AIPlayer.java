package citadels.model.player;

import citadels.model.card.*;
import citadels.model.game.CitadelsGame;
import java.util.*;
import java.util.stream.Collectors; //used to stream through cards

/**
 * AI-player denotes all players except player 1.
 * <p>Constraints (bishop immunity, build-limit, warlord cost, etc.) are implemented.</p>
 */
public final class AIPlayer extends Player {

    private static final Random RNG = new Random(); //random no. generator

    /** Constructor for AIPlayer */
    public AIPlayer(int id) {
        super(id);
    }

    @Override
    public void takeTurn(CitadelsGame game) {

        /* ---------- 0. Execute mandatory / early ability ---------- */
        switch (character.getRank()) {
            case 1:
                assassinTurn(game);    // Assassin
                break;
            case 2:
                thiefTurn(game);       // Thief
                break;
        }

        /* ---------- 1. Gather resources -------------------------- */
        gatherResources(game);

        /* ---------- 2. Architect extra 2 draw ---------------------- */
        if (character.getRank() == 7)   // Architect
            game.drawCards(this, 2);

        /* ---------- 3. Build districts --------------------------- */
        buildPhase(game);

        /* ---------- 4. Late abilities ---------------------------- */
        switch (character.getRank()) {
            case 3:
                magicianPostBuild(game);  // Magician
                break;
            case 8:
                warlordTurn(game);        // Warlord
                break;
        }
    }

    /* =============================================================
       RESOURCE CHOICE
       =========================================================== */

    /** Gather resources */
    private void gatherResources(CitadelsGame game) {
        //iterate through hand to check if player can afford at least one card
        boolean canAffordSomething = hand.stream()
                .anyMatch(c -> (c.getCost() <= gold));

        // ai feat. --> if broke or cannot build, prefer gold; else take cards if hand small
        if (!canAffordSomething && gold < 2) {
            game.collectGold(this); //collect gold at turnphase beginning
        } else if (hand.size() <= 2) {
            game.drawTwoChoose(this); //draw 2 cards and choose 1
        } else {
            game.collectGold(this); //if player has enough districts to build, collect gold
        }
    }

    /* =============================================================
       BUILD PHASE
       =========================================================== */

    /**
     * Builds districts up to the build limit if possible
     * @param game current game
     * @return void
     */
    private void buildPhase(CitadelsGame game) {
        // try to build up to limit each turn (Architect has limit 3)
        int limit = getBuildLimit();
        int built = 0; //counter for built districts

        while (built < limit) {
            Optional<DistrictCard> best = hand.stream()
                    .filter(c -> c.getCost() <= gold && !cityContains(c.getName()))
                    .sorted(buildComparator())
                    .findFirst(); //find first district that can be built

            if (!best.isPresent()) break; //if no district can be built, break (AI skips)
            game.buildDistrict(this, best.get()); //otherwise build district
            built++;
        }
    }
    
    /**
     * Comparator for building districts
     * @return Comparator<DistrictCard>
     */
    private static Comparator<DistrictCard> buildComparator() {
        return Comparator.<DistrictCard>comparingInt(DistrictCard::getCost).reversed()
                .thenComparing(c -> c.getColor() == DistrictColor.PURPLE ? 0 : 1) // prefer purple
                .thenComparing(DistrictCard::getName); //returns compared district card
    }

    /* =============================================================
       CHARACTER POWERS
       =========================================================== */

    /* ---------- Assassin (kill) ---------- */
    /**
     * Prioritizes killing the richest opponent
     * @param game current game
     * @return void
     */
    private void assassinTurn(CitadelsGame game) { //prioritizes killing the richest opponent
        Player target = richestOpponent(game, this);
        //randomly select a character from 2-8 if no target
        int victimRank = (target == null || target.getCharacter() == null)
                ? RNG.nextInt(7) + 2   // random 2-8 (because assassin is character 1)
                : target.getCharacter().getRank();
        game.killCharacter(victimRank); //kill the selected character
    }

    /* ---------- Thief (rob) -------------- */
    /**
     * Prioritizes robbing the richest opponent
     * @param game current game
     * @return void
     */
    private void thiefTurn(CitadelsGame game) { //prioritizes robbing the richest opponent
        Player target = richestOpponent(game, this);
        int victimRank = (target == null || target.getCharacter() == null)
                ? RNG.nextInt(6) + 3   // random 3-8 (because thief is character 2)
                : target.getCharacter().getRank();
        game.setRobTarget(this, victimRank); //set the target for robbing
    }

    /* ---------- Magician ----------------- */
    /**
     * Prioritizes swapping hands with the richest player
     * @param game current game
     * @return void
     */
    private void magicianPostBuild(CitadelsGame game) { //prioritizes swapping hands with the richest player
        // find player with most cards
        Player richestHand = game.getPlayers().stream()
                .filter(p -> p != this)
                .max(Comparator.comparingInt(p -> p.getHand().size()))
                .orElse(null); //filter out self, then find the player with the most cards

        //if the richest player has at least 2 more cards than magician, swap hands
        if (richestHand != null && richestHand.getHand().size() >= hand.size() + 2) {
            game.swapHands(this, richestHand);
        } else {
            // discard duplicates or high-cost unbuildable
            List<Integer> discIdx = new ArrayList<>();
            for (int i = 0; i < hand.size(); i++) {
                DistrictCard d = hand.get(i);
                boolean duplicate = cityContains(d.getName());
                boolean tooExpensive = d.getCost() > gold + 2;
                if (duplicate || tooExpensive) discIdx.add(i); //if duplicate or too expensive, discard
            }
            if (!discIdx.isEmpty()) { //if there are duplicates or too expensive, discard
                discIdx.sort(Comparator.reverseOrder()); //sort in descending order
                discIdx.forEach(i -> hand.remove((int) i)); //discard
                game.drawCards(this, discIdx.size()); //draw same amount
            }
        }
    }

    /* ---------- Warlord ------------------ */
    /**
     * Prioritizes destroying the cheapest destroyable district of a vulnerable player
     * @param game current game
     * @return void
     */
    private void warlordTurn(CitadelsGame game) {
        // choose cheapest destroyable district of a vulnerable player
        Player victim = game.getPlayers().stream()
                .filter(p -> p != this)
                .filter(p -> p.getCity().size() < 8)          // incomplete city
                .filter(p -> !game.isBishopProtected(p))      // does not have bishop immunity
                .min(Comparator.comparingInt(p ->
                        p.getCity().stream()
                                .mapToInt(DistrictCard::getCost)
                                .min().orElse(Integer.MAX_VALUE)))
                .orElse(null); //if no vulnerable player, return null

        if (victim == null) return; //if no vulnerable player, dont use ability

        int idx = -1; //index of the cheapest destroyable district
        int minCost = Integer.MAX_VALUE; //minimum cost of a destroyable district

        //iterate through the city of the vulnerable player
        //find the cheapest destroyable district
        for (int i = 0; i < victim.getCity().size(); i++) {
            DistrictCard d = victim.getCity().get(i);
            int trueCost = Math.max(0, d.getCost() - 1);
            if (trueCost <= gold && trueCost < minCost) {
                idx = i; minCost = trueCost;
            }
        }
        if (idx >= 0) game.destroyDistrict(this, victim, idx);
    }

    /* =============================================================
       Helper utilities
       =========================================================== */

/**
 * Choose a target for the Assassin (or Thief) AI.
 *
 * 1) Try to pick the richest computer-controlled player (AI) who is not 'self'.
 * 2) If there are no other AIs (for example, in a 2-player game), fall back to picking
 *    the richest player among all opponents (the human).
 * 
 * High preference for AI opponents, low preference for human player.
 *
 * @param g    the current game state
 * @param self the AI player invoking this method
 * @return     the chosen opponent player, or null if none exist
 */
private static Player richestOpponent(CitadelsGame g, Player self) {
    // filter out self, then only AIs, then find the one with max gold
    Optional<Player> richestAI = g.getPlayers().stream()
        .filter(p -> p != self)              // exclude self
        .filter(p -> p instanceof AIPlayer)  // only other AIs
        .max(Comparator.comparingInt(Player::getGold)); // richest among them

    // if at least one AI opponent found, return that richest AI
    if (richestAI.isPresent()) {
        return richestAI.get();
    }

    // if no other AIs available, fall back to richest among all opponents (human)
    return g.getPlayers().stream()
        .filter(p -> p != self)                          // exclude self
        .max(Comparator.comparingInt(Player::getGold))   // richest remaining
        .orElse(null);                                   // null if no opponents
}

}
