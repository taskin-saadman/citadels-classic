package citadels.cli;

import citadels.model.card.*;
import citadels.model.game.CitadelsGame;
import citadels.model.player.Player;
import org.json.simple.JSONObject; // used for saving and loading
import org.json.simple.parser.JSONParser; // used for loading

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

        boolean collected_resources = false; //will be used for the round's 2 gold collection by player 1
        boolean initial_choice_prompt_given = false; //will be used for initial choice prompt
        loop: // label for the loop (easy to break out of)
        while(true) {
            Command cmd = CommandParser.parse(prompt("> ")); 
            if (!initial_choice_prompt_given) {
                initial_choice_prompt_given = true;
            }

            switch (cmd.keyword()) {

    /* ------------------------------------------------------ */
    case "end":
        println("You ended your turn.");
        break loop; //break out of the infinite while loop

    /* ------------------------------------------------------ */
    case "hand":
        println("You have " + human.getGold() + " gold. Cards in hand:");
        List<DistrictCard> hand = human.getHand();
        for (int i = 0; i < hand.size(); i++) {
            DistrictCard card = hand.get(i);
            println((i + 1) + ". " + card.getName() + " (" +
                    card.getColor().toString().toLowerCase() + "), cost: " + card.getCost());
        }
        break;

    case "gold": {
        //first time collection command in a round
        if (!collected_resources) {game.collectGold(human); collected_resources = true; break;}

        //rest part is to check current gold count after user either collected gold or drew 2 district cards
        int seat = cmd.args().isEmpty()
                   ? human.getId()                    // default to current player
                   : Integer.parseInt(cmd.arg(0, "1")) - 1;  // 1-based → 0-based

        //handle index out of bounds
        if (seat < 0 || seat >= game.getPlayers().size()) {
            println("Invalid player number. Enter a number between 1 and " + game.getPlayers().size() + ".");
            break;
        }

        Player target = game.getPlayers().get(seat);
        println("Player " + (seat + 1) + " has " + target.getGold() + " gold.");
        break;
    }

    case "build":
        if (cmd.args().isEmpty()) {
            println("build <index>"); //error message for no args
            break;
        }
        try {
            int idx = Integer.parseInt(cmd.arg(0, "-1")) - 1;
            game.buildDistrict(human, human.getHand().get(idx));
        } catch (Exception e) {
            println("Invalid index. Enter a number between 1 and " + human.getHand().size() + ".");
        }
        break;

    /* ------------------------------------------------------ */
    /*  NEW: optional [p] parameter for city/citadel/list      */
    case "city":
    case "citadel":
    case "list": { //either city , citadel or list was entered
    //gold count, built districts is public info in this game
    //handle index out of bounds
        int seat = cmd.args().isEmpty()
                   ? 0                              // default to player 1
                   : Integer.parseInt(cmd.arg(0, "1")) - 1;  // 1-based → 0-based

        //handle index out of bounds
        if (seat < 0 || seat >= game.getPlayers().size()) {
            println("Invalid player number. Enter a number between 1 and " + game.getPlayers().size() + ".");
            break;
        }

        Player target = game.getPlayers().get(seat);          // assume sane input
        List<DistrictCard> city = target.getCity();  

        println("Player " + (seat + 1) + " city:");
        if (city.isEmpty()) {
            println("no districts built yet!");
        } else {
            for (DistrictCard d : city) println("  " + d.toString());
        }
        break;
    }

    case "all":
        {
            List<Player> players = game.getPlayers();
            
            for (Player p : players) {
                StringBuilder sb = new StringBuilder();
                sb.append("Player ").append(p.getId() + 1);
                
                // Add "(you)" for the human player
                if (p.getId() == 0) {  // Human player is always player 0
                    sb.append(" (you)");
                }
                
                // Add cards count
                sb.append(": cards=").append(p.getHand().size());
                
                // Add gold count
                sb.append(" gold=").append(p.getGold());
                
                // Add city information
                sb.append(" city=");
                List<DistrictCard> city = p.getCity();
                if (city.isEmpty()) {
                    sb.append("");
                } else {
                    for (int i = 0; i < city.size(); i++) {
                        DistrictCard card = city.get(i);
                        sb.append(card.getName())
                          .append(" [")
                          .append(card.getColor().toString().toLowerCase())
                          .append(card.getCost())
                          .append("]");
                        if (i < city.size() - 1) {
                            sb.append(", ");
                        }
                    }
                }
                
                println(sb.toString());
            }
            break;
        }

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
    case "cards":
        if (collected_resources) { println("You already gathered resources this round!"); break;}
        game.drawTwoChoose(human);
        collected_resources = true;
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

    /* ---------------- help & default ---------------------- */
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
            case 1: // Assassin (enters "kill <2-8>")
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
                println("Your character's ability is automatic or already applied.");
        }
    }

    static String actionHelp(CharacterCard ch) {
        switch (ch.getRank()) {
            case 1:  return "Who do you want to kill? Choose a character from 2-8:";
            case 2:  return "Who do you want to steal from? Choose a character from 3-8:";
            case 3:  return "choose action [swap/redraw/none]:";
            case 4:  return "Automatic: gains gold for yellow districts and takes crown";
            case 5:  return "Automatic: gains gold for blue districts and gets immunity";
            case 6:  return "Automatic: gains gold for green districts + 1 extra gold";
            case 7:  return "Automatic: draws 2 extra cards and may build 3 districts";
            case 8:  return "Destroy a district? [y/N]:";
            default: return "";
        }
    }

    static void printHelp() {
        System.out.println("Available commands:");
        System.out.println("info : show information about a character or building");
        System.out.println("t : processes turns");
        System.out.println("all : shows all current game info");
        System.out.println("citadel/list/city : shows districts built by a player");
        System.out.println("hand : shows cards in hand");
        System.out.println("gold [p] : shows gold of a player");
        System.out.println("build <place in hand> : Builds a building into your city");
        System.out.println("action : Gives info about your special action and how to perform it");
        System.out.println("end : Ends your turn");
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
                        : " - " + d.getSpecialText()));
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
