// src/test/java/citadels/GameEndScoringTest.java
package citadels;

import citadels.cli.CommandHandler;
import citadels.model.card.DistrictCard;
import citadels.model.card.DistrictColor;
import citadels.model.game.*;
import citadels.model.player.Player;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/** Simple unit test for ScoreCalculator edge-cases. */
public class GameEndScoringTest {

    @Test
    public void testDiversityAndBonus() {
        // dummy CLI
        CommandHandler cli = new CommandHandler() {
            public void println(String msg) {}
            public String prompt(String m) { return ""; }
        };
        CitadelsGame g = new CitadelsGame(4, cli);

        Player p = g.getPlayers().get(0);
        // Fake-build one of each colour
        p.addDistrictToCity(new DistrictCard("Castle",  DistrictColor.YELLOW, 4, null));
        p.addDistrictToCity(new DistrictCard("Temple",  DistrictColor.BLUE,   1, null));
        p.addDistrictToCity(new DistrictCard("Tavern",  DistrictColor.GREEN,  1, null));
        p.addDistrictToCity(new DistrictCard("Watch",   DistrictColor.RED,    1, null));
        p.addDistrictToCity(new DistrictCard("School",  DistrictColor.PURPLE, 6, null));

        Map<Player,Integer> scores = ScoreCalculator.compute(g.getPlayers(), -1);
        int expected = 4+1+1+1+6   // costs
                     + 3;          // diversity
        assertEquals(expected, (int) scores.get(p));
    }
}
