package citadels.model.character;

import citadels.model.card.CharacterCard;
import citadels.model.game.CitadelsGame;
import citadels.model.player.Player;

/** Rank 2 – Thief */
public final class Thief extends CharacterCard {

    public Thief() {
        super("Thief", 2);
    }

    @Override
    public void use(CitadelsGame game, Player self) {
        int victimRank = game.promptCharacterSelection(
                self, 3, 8, "Who do you want to steal from? Choose a character from 3-8:");
        game.setRobTarget(self, victimRank);
    }
}
