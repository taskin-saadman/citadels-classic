package citadels.cli;

import citadels.model.card.*;
import citadels.model.game.CitadelsGame;
import citadels.model.player.Player;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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

        loop:
        while (true) {
            Command cmd = CommandParser.parse(prompt("> "));

            switch (cmd.keyword()) {

                /* ------------------------------------------------------ */
                case "t":
                case "end":
                    println("You ended your turn.");
                    break loop;

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
                    if (cmd.args().isEmpty()) {
                        println("build <index>");
                        break;
                    }
                    try {
                        int idx = Integer.parseInt(cmd.arg(0, "-1")) - 1;
                        game.buildDistrict(human, human.getHand().get(idx));
                    } catch (Exception e) {
                        println("Invalid index.");
                    }
                    break;

                case "city":
                case "citadel":
                case "list":
                    if (human.getCity().isEmpty())
                        println("You have built nothing.");
                    else
                        for (DistrictCard d : human.getCity()) println(d.toString());
                    break;

                /* ---------------- action / info ----------------------- */
                case "action":
                    handleActionCommand(game, human, cmd.args());
                    break;

                case "info":
                    if (cmd.args().isEmpty()) {
                        println("info <name|index>");
                        break;
                    }
                    infoCommand(game, human, cmd.arg(0, ""));
                    break;

                /* ---------------- collect / draw ---------------------- */
                case "collect":
                    if (!cmd.args().isEmpty() &&
                        "gold".equalsIgnoreCase(cmd.arg(0, ""))) {
                        game.collectGold(human);
                    } else {
                        println("Usage: collect gold");
                    }
                    break;

                case "cards":
                    game.drawTwoChoose(human);
                    break;

                /* ---------------- save / load ------------------------- */
                case "save":
                    if (cmd.args().isEmpty()) {
                        println("save <file>");
                        break;
                    }
                    saveGame(game, cmd.arg(0, ""));
                    break;

                case "load":
                    if (cmd.args().isEmpty()) {
                        println("load <file>");
                        break;
                    }
                    CitadelsGame newG = loadGame(cmd.arg(0, ""), io);
                    if (newG != null) io.attachGame(newG);
                    break;

                /* ---------------- debug flag -------------------------- */
                case "debug":
                    io.toggleDebug();
                    println("Debug " + (io.isDebug() ? "enabled." : "disabled."));
                    break;

                /* ---------------- help -------------------------- */
                case "help":
                default:
                    printHelp();
            }
        }
    }

    /* -------------------------------------------------------- *
     *  Action command handler (Thief, Magician, etc.)          *
     * -------------------------------------------------------- */
    default void handleActionCommand(CitadelsGame game,
                                     Player human,
                                     List<String> args) {
        CharacterCard ch = human.getCharacter();
        int rank = ch.getRank();

        if (args.isEmpty()) {
            println("Usage depends on your character:");
            println(actionHelp(ch));
            return;
        }

        switch (rank) {
            case 1: // Assassin
                if (args.size() == 2 && "kill".equalsIgnoreCase(args.get(0))) {
                    int r = parseRank(args.get(1));
                    if (r >= 2 && r <= 8) {
                        game.killCharacter(r);
                        println("You chose to kill rank " + r + ".");
                    } else println("Rank must be 2-8.");
                } else println("Format: action kill <2-8>");
                break;

            case 2: // Thief
                if (args.size() == 2 && "steal".equalsIgnoreCase(args.get(0))) {
                    int r = parseRank(args.get(1));
                    if (r >= 3 && r <= 8) {
                        game.setRobTarget(human, r);
                        println("You chose to steal from rank " + r + ".");
                    } else println("Rank must be 3-8.");
                } else println("Format: action steal <3-8>");
                break;

            case 3: // Magician
                String sub = args.get(0).toLowerCase();
                if ("swap".equals(sub) && args.size() == 2) {
                    int seat = parseInt(args.get(1), -1) - 1;
                    if (seat >= 0 && seat < game.getPlayers().size()
                            && seat != human.getId()) {
                        game.swapHands(human, game.getPlayers().get(seat));
                        println("Swapped hands with player " + (seat + 1) + ".");
                    } else println("Invalid player number.");
                } else if ("redraw".equals(sub) && args.size() == 2) {
                    String[] parts = args.get(1).split(",");
                    List<Integer> indexes = new java.util.ArrayList<>();
                    for (String s : parts)
                        indexes.add(parseInt(s.trim(), -1) - 1);

                    int count = 0;
                    for (int i = indexes.size() - 1; i >= 0; i--) {
                        int idx = indexes.get(i);
                        if (idx >= 0 && idx < human.getHand().size()) {
                            human.getHand().remove(idx);
                            count++;
                        }
                    }
                    game.drawCards(human, count);
                    println("Redrew " + count + " card(s).");
                } else {
                    println("swap <player#> OR redraw <idx,idx,...>");
                }
                break;

            case 8: // Warlord
                if (args.size() == 3 && "destroy".equalsIgnoreCase(args.get(0))) {
                    int seat = parseInt(args.get(1), -1) - 1;
                    int idx  = parseInt(args.get(2), -1) - 1;
                    if (seat >= 0 && seat < game.getPlayers().size()) {
                        Player victim = game.getPlayers().get(seat);
                        game.destroyDistrict(human, victim, idx);
                    } else println("Invalid player number.");
                } else println("Format: action destroy <player#> <district#>");
                break;

            default:
                println("Your character’s ability is automatic or already applied.");
        }
    }

    static String actionHelp(CharacterCard ch) {
        switch (ch.getRank()) {
            case 1:  return "kill <2-8>";
            case 2:  return "steal <3-8>";
            case 3:  return "swap <player #> OR redraw <indexes>";
            case 4:  return "Automatic: gains gold for yellow & crown.";
            case 5:  return "Automatic: gains gold for blue; immunity.";
            case 6:  return "Automatic: gains gold for green +1.";
            case 7:  return "Automatic: draws 2 extra; may build 3.";
            case 8:  return "destroy <player #> <district #>";
            default: return "";
        }
    }

    static void printHelp() {
        System.out.println("Available commands:");
        System.out.println("  t / end              finish your turn");
        System.out.println("  hand                 show cards & gold");
        System.out.println("  collect gold         take 2 gold");
        System.out.println("  cards                draw 2 cards (Library keeps both)");
        System.out.println("  build <n>            build hand card #n");
        System.out.println("  city                 show your city");
        System.out.println("  action ...           perform your character ability");
        System.out.println("  info <x>             details on card or character");
        System.out.println("  save <file>          save game");
        System.out.println("  load <file>          load game");
        System.out.println("  debug                toggle AI hand visibility");
    }

    static int parseRank(String s) {
        return parseInt(s, -1);
    }

    static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    static void infoCommand(CitadelsGame g, Player h, String token) {
        try {
            int idx = Integer.parseInt(token) - 1;
            DistrictCard d = h.getHand().get(idx);
            printDistrictInfo(d);
            return;
        } catch (Exception ignore) { }

        for (int r = 1; r <= 8; r++)
            if (token.equalsIgnoreCase(g.rankName(r))) {
                System.out.println(actionHelp(g.findCharacterCard(r)));
                return;
            }

        for (DistrictCard d : h.getHand())
            if (d.getName().equalsIgnoreCase(token)) {
                printDistrictInfo(d); return;
            }

        System.out.println("No such card or character.");
    }

    static void printDistrictInfo(DistrictCard d) {
        System.out.println(d +
                (d.getSpecialText() == null ? ""
                        : " — " + d.getSpecialText()));
    }

    static void saveGame(CitadelsGame g, String file) {
        try (FileWriter fw = new FileWriter(file)) {
            JSONObject js = citadels.model.game.GameState.serialise(g);
            js.writeJSONString(fw);
            System.out.println("Game saved to " + file);
        } catch (IOException e) {
            System.out.println("Save failed: " + e.getMessage());
        }
    }

    static CitadelsGame loadGame(String file, ConsoleHandler io) {
        try (FileReader fr = new FileReader(file)) {
            JSONObject js = (JSONObject) new JSONParser().parse(fr);
            CitadelsGame g = citadels.model.game.GameState.deserialise(js, io, io.cardRepo());
            System.out.println("Game loaded.");
            return g;
        } catch (Exception e) {
            System.out.println("Load failed: " + e.getMessage());
            return null;
        }
    }
}
