package citadels.model.character;

import citadels.model.card.CharacterCard;
import citadels.model.game.CitadelsGame;
import citadels.model.player.Player;

/** Rank 3 – Magician */
public final class Magician extends CharacterCard {

    public Magician() {
        super("Magician", 3);
    }

    @Override
    public void use(CitadelsGame game, Player self) {
        String choice = game.cli().prompt(
                "Magician — choose action [swap/redraw/none]:").trim().toLowerCase();

        switch (choice) {
            case "swap":
                Player target = game.promptPlayerSelection(
                        self, "Swap hands with which player?");
                game.swapHands(self, target);
                break;

            case "redraw":
                int count = game.promptAndDiscardCards(self,
                        "Enter hand indices (comma-separated) to discard and redraw:");
                game.drawCards(self, count);
                break;

            default:
                /* ability skipped */
        }
    }
}
