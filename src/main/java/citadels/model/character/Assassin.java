package citadels.model.character;

import citadels.model.card.CharacterCard;
import citadels.model.game.CitadelsGame;
import citadels.model.player.Player;

/** Rank 1 – Assassin */
public final class Assassin extends CharacterCard {

    public Assassin() {
        super("Assassin", 1);
    }

    @Override
    public void use(CitadelsGame game, Player self) {
        int victimRank = game.promptCharacterSelection(
                self, 2, 8, "Who do you want to kill? Choose a character from 2-8:");
        game.killCharacter(victimRank);
    }
}
