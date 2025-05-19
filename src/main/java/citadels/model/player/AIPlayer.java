// src/main/java/citadels/model/player/AIPlayer.java
package citadels.model.player;

import citadels.model.card.DistrictCard;
import citadels.model.game.CitadelsGame;

import java.util.Comparator;

/**
 * A very simple heuristic-based computer opponent.
 *
 * <p>You can refine the logic any time, but this minimal version already
 * chooses between gold/cards, builds the priciest affordable district, and
 * invokes its character power exactly once.</p>
 */
public final class AIPlayer extends Player {

    public AIPlayer(int id) {
        super(id);
    }

    @Override
    public void takeTurn(CitadelsGame game) {

        /* -------- 0) Character power (Thief/Assassin must act early) ------- */
        if (character != null) {
            character.use(game, this);
        }

        /* -------- 1) Gather resources ------------------------------------- */
        if (hand.isEmpty()) {                     // starving for cards → draw
            game.drawCards(this, 2);
        } else if (gold < 2) {                    // broke → take gold
            game.collectGold(this);
        } else {
            // if we can already afford ≥1 district, prefer gold, else hunt cards
            boolean canAfford = hand.stream().anyMatch(c -> c.getCost() <= gold);
            if (canAfford) game.collectGold(this); else game.drawCards(this, 2);
        }

        /* -------- 2) Build (most expensive we can afford and don’t already own) */
        hand.stream()
            .filter(c -> c.getCost() <= gold && !cityContains(c.getName()))
            .max(Comparator.comparingInt(DistrictCard::getCost))
            .ifPresent(best -> game.buildDistrict(this, best));

        /* -------- 3) Any post-resource character power (Architect, etc.) --- */
        // For simplicity we won’t call use() again; Architect’s buildLimit is
        // handled inside its ability above. This is “good enough” for now.
    }
}
