// src/main/java/citadels/model/player/HumanPlayer.java
package citadels.model.player;

import citadels.model.game.CitadelsGame;

/**
 * Delegates its entire turn flow to the CLI handler injected in {@link CitadelsGame}.
 */
public final class HumanPlayer extends Player {

    public HumanPlayer(int id) {
        super(id);
    }

    @Override
    public void takeTurn(CitadelsGame game) {
        game.cli().humanTurnLoop(this);   // the CLI layer drives everything interactively
    }
}
