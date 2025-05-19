// src/main/java/citadels/cli/CommandHandler.java
package citadels.cli;

import citadels.model.card.DistrictCard;
import citadels.model.game.CitadelsGame;
import citadels.model.player.Player;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * Abstraction for user I/O. The concrete {@link ConsoleHandler}
 * drives Terminal interaction, but a mock can be injected in tests.
 */
public interface CommandHandler {

    /* ------------ basic I/O ------------ */

    void println(String msg);
    String prompt(String msg);

    /* ------------ high-level helper ------------ */

    /**
     * Interactive loop used whenever the human player is taking a turn.
     * <p>Default implementation handles a minimal command-set
     * and delegates to {@link CitadelsGame} helpers that already exist
     * in the engine skeleton.</p>
     */
    default void humanTurnLoop(Player human) {
        if (!(this instanceof ConsoleHandler)) return;  // only console impl

        ConsoleHandler io = (ConsoleHandler) this;
        CitadelsGame game = io.getGame();

        while (true) {
            Command cmd = CommandParser.parse(prompt("> "));
            switch (cmd.keyword()) {

                case "t":   // continue (mainly for AI / auto-advance)
                    return;

                case "hand":
                    game.cli().println("You have " + human.getGold() + " gold.");
                    for (int i = 0; i < human.getHand().size(); i++) {
                        DistrictCard c = human.getHand().get(i);
                        println((i + 1) + ". " + c);
                    }
                    break;

                case "gold":
                    println("You have " + human.getGold() + " gold.");
                    break;

                case "build":
                    int idx;
                    try { idx = Integer.parseInt(cmd.arg(0, "-1")) - 1; }
                    catch (NumberFormatException ex) { idx = -1; }
                    if (idx < 0 || idx >= human.getHand().size()) {
                        println("Invalid index.");
                    } else {
                        game.buildDistrict(human, human.getHand().get(idx));
                    }
                    break;

                case "save":
                    if (cmd.args().isEmpty()) { println("save <file>"); break; }
                    try (FileWriter out = new FileWriter(cmd.arg(0, ""))) {
                        JSONObject js = GameState.serialise(game);
                        js.writeJSONString(out);
                        println("Saved.");
                    } catch (IOException ex) { println("Error: " + ex.getMessage()); }
                    break;

                case "load":
                    if (cmd.args().isEmpty()) { println("load <file>"); break; }
                    try (FileReader in = new FileReader(cmd.arg(0, ""))) {
                        JSONObject js = (JSONObject) new JSONParser().parse(in);
                        game = GameState.deserialise(js, this, CardRepo.INSTANCE);
                        attachGame(game);
                        println("Loaded.");
                    } catch (Exception ex) { println("Error: " + ex.getMessage()); }
                    break;

                case "city":
                case "citadel":
                case "list":
                    if (human.getCity().isEmpty()) {
                        println("You have built nothing.");
                    } else {
                        human.getCity().forEach(d -> println(d.toString()));
                    }
                    break;

                case "end":
                    return;

                case "help":
                default:
                    println("Commands: hand | gold | build <n> | city | end");
            }
        }
    }
}
