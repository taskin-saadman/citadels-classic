package citadels.model.player;

import citadels.model.game.CitadelsGame;

/**
 * Delegates its entire turn flow to the CLI handler injected in {@link CitadelsGame}.
 */
public final class HumanPlayer extends Player {

    /**
     * Constructor for HumanPlayer
     * @param id id of the player
     */
    public HumanPlayer(int id) {
        super(id);
    }

    /**
     * Delegates its entire turn flow to the CLI handler injected in {@link CitadelsGame}.
     * @param game current game
     */
    @Override
    public void takeTurn(CitadelsGame game) {
        game.cli().humanTurnLoop(this);   // the CLI layer drives everything interactively
    }
}
