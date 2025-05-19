package citadels.model.character;

import citadels.model.card.CharacterCard;
import citadels.model.card.DistrictColor;
import citadels.model.game.CitadelsGame;
import citadels.model.player.Player;

/** Rank 5 – Bishop */
public final class Bishop extends CharacterCard {

    public Bishop() {
        super("Bishop", 5);
    }

    @Override
    public void use(CitadelsGame game, Player self) {
        game.gainGoldForColor(self, DistrictColor.BLUE);
        game.setBishopProtection(self, true);
    }
}
