// src/main/java/citadels/model/character/Ability.java
package citadels.model.character;

import citadels.model.game.CitadelsGame;
import citadels.model.player.Player;

/**
 * Strategy interface for all character powers.
 * Every concrete character class supplies its own implementation.
 */
public interface Ability {

    /**
     * Perform the character’s once-per-round ability.
     *
     * @param game  reference to the mutable game engine
     * @param self  the player who owns the character this round
     */
    void use(CitadelsGame game, Player self);
}
