package citadels.model.character;

import citadels.model.card.CharacterCard;
import citadels.model.card.DistrictColor;
import citadels.model.game.CitadelsGame;
import citadels.model.player.Player;

/** Rank 8 – Warlord */
public final class Warlord extends CharacterCard {

    public Warlord() {
        super("Warlord", 8);
    }

    @Override
    public void use(CitadelsGame game, Player self) {
        game.gainGoldForColor(self, DistrictColor.RED);

        if (game.cli().prompt("Destroy a district? [y/N]:")
                .trim().equalsIgnoreCase("y")) {

            Player target = game.promptPlayerSelection(
                    self, "Choose a player whose district to destroy:");
            int districtIndex = game.promptDistrictSelection(
                    target, "Choose district index to destroy:");
            game.destroyDistrict(self, target, districtIndex);
        }
    }
}
