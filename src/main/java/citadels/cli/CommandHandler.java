package citadels.cli;

import citadels.model.card.*;
import citadels.model.character.CharacterCard;
import citadels.model.game.*;
import citadels.model.player.Player;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.util.List;

public interface CommandHandler {

    /* low-level I/O */
    void println(String msg);
    String prompt(String msg);

    /* ------------------------------------------------------------------ *
     *  Interactive loop for the human player                             *
     * ------------------------------------------------------------------ */
    default void humanTurnLoop(Player human) {

        ConsoleHandler io   = (ConsoleHandler) this;
        CitadelsGame   game = io.getGame();

        while (true) {
            Command cmd = CommandParser.parse(prompt("> "));

            switch (cmd.keyword()) {

                /* ------------------------------------------------------ */
                case "t":         // continue / finish AI turns
                case "end":
                    return;

                /* ------------------------------------------------------ */
                case "hand":
                    println("You have " + human.getGold() + " gold.");
                    List<DistrictCard> hand = human.getHand();
                    for (int i = 0; i < hand.size(); i++)
                        println((i + 1) + ". " + hand.get(i));
                    break;

                case "gold":
                    println("You have " + human.getGold() + " gold.");
                    break;

                case "build":
                    if (cmd.args().isEmpty()) { println("build <index>"); break; }
                    try {
                        int idx = Integer.parseInt(cmd.arg(0, "-1")) - 1;
                        game.buildDistrict(human, human.getHand().get(idx));
                    } catch (Exception e) { println("Invalid index."); }
                    break;

                case "city":
                case "citadel":
                case "list":
                    if (human.getCity().isEmpty())
                        println("You have built nothing.");
                    else human.getCity().forEach(d -> println(d.toString()));
                    break;

                /* ---------------- action / info ----------------------- */
                case "action":
                    CharacterCard ch = human.getCharacter();
                    println("Special ability of " + ch.getName() + ":");
                    // naive summary – could be richer
                    println(actionHelp(ch));
                    break;

                case "info":           // purple card or character
                    if (cmd.args().isEmpty()) { println("info <name|index>"); break; }
                    infoCommand(game, human, cmd.arg(0, ""));
                    break;

                /* ---------------- save / load ------------------------- */
                case "save":
                    if (cmd.args().isEmpty()) { println("save <file>"); break; }
                    saveGame(game, cmd.arg(0, ""));
                    break;

                case "load":
                    if (cmd.args().isEmpty()) { println("load <file>"); break; }
                    CitadelsGame newG = loadGame(cmd.arg(0, ""), io);
                    if (newG != null) io.attachGame(newG);
                    break;

                /* ---------------- debug flag -------------------------- */
                case "debug":
                    io.toggleDebug();
                    println("Debug " + (io.isDebug() ? "enabled." : "disabled."));
                    break;

                /* ------------------------------------------------------ */
                case "help":
                default:
                    println("""
                            Commands:
                              t / end          – finish your turn
                              hand             – show cards + gold
                              build <n>        – build card #n from hand
                              city             – show your city
                              action           – remind how to use your power
                              info <x>         – details on card or character
                              save <file>      – save game to JSON
                              load <file>      – load game from JSON
                              debug            – toggle AI hand visibility""");
            }
        }
    }

    /* ---------------- command helpers ------------------- */

    private static String actionHelp(CharacterCard ch) {
        return switch (ch.getRank()) {
            case 1 -> "kill <2-8>";
            case 2 -> "steal <3-8>";
            case 3 -> "swap <player #>  OR  redraw <indexes>";
            case 4 -> "automatically gains gold for yellow and the crown.";
            case 5 -> "gains gold for blue; city immune to Warlord.";
            case 6 -> "gains gold for green + 1 extra.";
            case 7 -> "draw 2 extra cards; may build up to 3 districts.";
            case 8 -> "destroy <player #> <district #>";
            default -> "";
        };
    }

    private static void infoCommand(CitadelsGame g, Player h, String token) {
        try {                                   // numeric hand index?
            int idx = Integer.parseInt(token) - 1;
            DistrictCard d = h.getHand().get(idx);
            printDistrictInfo(d);
            return;
        } catch (Exception ignore) {}

        // look for character
        for (int r = 1; r <= 8; r++)
            if (token.equalsIgnoreCase(g.rankName(r))) {
                System.out.println(actionHelp(g.findCharacterCard(r)));
                return;
            }

        // look for purple district by name
        for (DistrictCard d : h.getHand())
            if (d.getName().equalsIgnoreCase(token)) {
                printDistrictInfo(d);  return;
            }
        System.out.println("No such card or character.");
    }

    private static void printDistrictInfo(DistrictCard d) {
        System.out.println(d +
                (d.getSpecialText() == null ? "" : " — " + d.getSpecialText()));
    }

    private static void saveGame(CitadelsGame g, String file) {
        try (FileWriter fw = new FileWriter(file)) {
            JSONObject js = GameState.serialise(g);
            js.writeJSONString(fw);
            System.out.println("Game saved to " + file);
        } catch (IOException e) {
            System.out.println("Save failed: " + e.getMessage());
        }
    }

    private static CitadelsGame loadGame(String file, ConsoleHandler io) {
        try (FileReader fr = new FileReader(file)) {
            JSONObject js = (JSONObject) new JSONParser().parse(fr);
            CitadelsGame g = GameState.deserialise(js, io, io.cardRepo());
            System.out.println("Game loaded.");
            return g;
        } catch (Exception e) {
            System.out.println("Load failed: " + e.getMessage());
            return null;
        }
    }
}
