package citadels.model.character;

import citadels.model.card.CharacterCard;
import citadels.model.card.DistrictColor;
import citadels.model.game.CitadelsGame;
import citadels.model.player.Player;

/** Rank 6 – Merchant */
public final class Merchant extends CharacterCard {

    public Merchant() {
        super("Merchant", 6);
    }

    @Override
    public void use(CitadelsGame game, Player self) {
        game.gainGoldForColor(self, DistrictColor.GREEN);
        game.gainGold(self, 1);        // extra gold
    }
}
