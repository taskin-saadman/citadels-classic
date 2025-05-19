package citadels.model.character;

import citadels.model.card.CharacterCard;
import citadels.model.game.CitadelsGame;
import citadels.model.player.Player;

/** Rank 7 – Architect */
public final class Architect extends CharacterCard {

    public Architect() {
        super("Architect", 7);
    }

    @Override
    public void use(CitadelsGame game, Player self) {
        game.drawCards(self, 2);       // two extra cards
        game.setBuildLimit(self, 3);   // may build up to 3 districts
    }
}
