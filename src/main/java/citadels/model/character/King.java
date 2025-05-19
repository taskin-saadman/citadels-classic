package citadels.model.character;

import citadels.model.card.CharacterCard;
import citadels.model.card.DistrictColor;
import citadels.model.game.CitadelsGame;
import citadels.model.player.Player;

/** Rank 4 – King */
public final class King extends CharacterCard {

    public King() {
        super("King", 4);
    }

    @Override
    public void use(CitadelsGame game, Player self) {
        game.gainGoldForColor(self, DistrictColor.YELLOW);
        game.takeCrown(self);
    }
}
