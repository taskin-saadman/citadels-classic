package citadels.cli;

import citadels.model.card.*;
import citadels.model.game.*;
import citadels.model.player.Player;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.util.List;

public interface CommandHandler {

    /* ---------- low-level I/O ---------- */
    void println(String msg);
    String prompt(String msg);

    /* ------------------------------------------------------------------ *
     *  Interactive loop for the human player                             *
     * ------------------------------------------------------------------ */
    default void humanTurnLoop(Player human) {

        ConsoleHandler io = (ConsoleHandler) this;
        CitadelsGame   game = io.getGame();

        while (true) {

            Command cmd = CommandParser.parse(prompt("> "));

            switch (cmd.keyword()) {

                /* -------------------------------------------------- */
                case "t":           /* finish turn / advance */
                case "end":
                    return;

                /* -------------------------------------------------- */
                case "hand":
                    println("You have " + human.getGold() + " gold.");
                    List<DistrictCard> hand = human.getHand();
                    for (int i = 0; i < hand.size(); i++)
                        println((i + 1) + ". " + hand.get(i));
                    break;

                /* view current gold */
                case "gold":
                    println("You have " + human.getGold() + " gold.");
                    break;

                /* ------- RESOURCE COLLECTION ----------- */
                /* collect 2 gold */
                case "collect":
                    if (!cmd.args().isEmpty() &&
                        "gold".equalsIgnoreCase(cmd.arg(0, ""))) {
                        game.collectGold(human);
                        break;
                    }
                    println("Usage: collect gold   (or use 'cards')");
                    break;

                /* draw two cards and keep one/both (Library) */
                case "cards":
                    game.drawTwoChoose(human);
                    break;

                /* --------------- build ---------------- */
                case "build":
                    if (cmd.args().isEmpty()) { println("build <index>"); break; }
                    try {
                        int idx = Integer.parseInt(cmd.arg(0, "-1")) - 1;
                        game.buildDistrict(human, human.getHand().get(idx));
                    } catch (Exception e) { println("Invalid index."); }
                    break;

                /* -------------- city info ------------- */
                case "city":
                case "citadel":
                case "list":
                    if (human.getCity().isEmpty())
                        println("You have built nothing.");
                    else
                        for (DistrictCard d : human.getCity()) println(d.toString());
                    break;

                /* -------- action / info ---------- */
                case "action":
                    CharacterCard ch = human.getCharacter();
                    println("Special ability of " + ch.getName() + ":");
                    println(actionHelp(ch));
                    break;

                case "info":
                    if (cmd.args().isEmpty()) { println("info <name|index>"); break; }
                    infoCommand(game, human, cmd.arg(0, ""));
                    break;

                /* ------------- save / load ------------- */
                case "save":
                    if (cmd.args().isEmpty()) { println("save <file>"); break; }
                    saveGame(game, cmd.arg(0, ""));
                    break;

                case "load":
                    if (cmd.args().isEmpty()) { println("load <file>"); break; }
                    CitadelsGame newG = loadGame(cmd.arg(0, ""), io);
                    if (newG != null) io.attachGame(newG);
                    break;

                /* -------------- debug toggle ----------- */
                case "debug":
                    io.toggleDebug();
                    println("Debug " + (io.isDebug() ? "enabled." : "disabled."));
                    break;

                /* -------------- help / default --------- */
                case "help":
                default:
                    println("Available commands:");
                    println("  t / end            – finish your turn");
                    println("  hand               – show cards & gold");
                    println("  collect gold       – take 2 gold");
                    println("  cards              – draw 2 cards and keep 1 (Library keeps both)");
                    println("  build <n>          – build hand card #n");
                    println("  city               – show your city");
                    println("  action             – show how to use your power");
                    println("  info <x>           – details on card or character");
                    println("  save <file>        – save game");
                    println("  load <file>        – load game");
                    println("  debug              – toggle AI hand visibility");
            }
        }
    }

    /* ---------------- command helpers ------------------- */

    private static String characterName(int rank) {
        switch (rank) {
            case 1: return "Assassin";
            case 2: return "Thief";
            case 3: return "Magician";
            case 4: return "King";
            case 5: return "Bishop";
            case 6: return "Merchant";
            case 7: return "Architect";
            case 8: return "Warlord";
            default: return "?";
        }
    }

    /* Simple textual reminder of abilities (Java-8 switch) */
    private static String actionHelp(CharacterCard ch) {
        switch (ch.getRank()) {
            case 1:  return "kill <2-8>";
            case 2:  return "steal <3-8>";
            case 3:  return "swap <player #>  OR  redraw <indexes>";
            case 4:  return "Gains gold for yellow districts and the crown.";
            case 5:  return "Gains gold for blue; city immune to Warlord.";
            case 6:  return "Gains gold for green + 1 extra.";
            case 7:  return "Draw 2 extra cards; may build up to 3 districts.";
            case 8:  return "destroy <player #> <district #>";
            default: return "";
        }
    }

    private static void infoCommand(CitadelsGame g, Player h, String token) {

        /* try numeric hand index first */
        try {
            int idx = Integer.parseInt(token) - 1;
            DistrictCard d = h.getHand().get(idx);
            printDistrictInfo(d);
            return;
        } catch (Exception ignore) { }

        /* check if token matches a character name */
        for (int r = 1; r <= 8; r++) {
            if (token.equalsIgnoreCase(characterName(r))) {
                System.out.println(actionHelp(
                        citadels.util.CardRepoSingleton.INSTANCE.characterByRank(r)));
                return;
            }
        }

        /* finally, check for district name in hand */
        for (DistrictCard d : h.getHand()) {
            if (d.getName().equalsIgnoreCase(token)) {
                printDistrictInfo(d);
                return;
            }
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
            CitadelsGame g =
                    GameState.deserialise(js, io, io.cardRepo());
            System.out.println("Game loaded.");
            return g;
        } catch (Exception e) {
            System.out.println("Load failed: " + e.getMessage());
            return null;
        }
    }
}
