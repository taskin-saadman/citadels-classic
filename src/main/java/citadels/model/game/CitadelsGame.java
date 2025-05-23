package citadels.model.game;

import citadels.cli.CommandHandler;
import citadels.cli.ConsoleHandler;
import citadels.model.card.*;
import citadels.model.character.*;
import citadels.model.player.*;
import citadels.util.TSVLoader;
import java.util.*;

/**
 * Core game controller: maintains mutable state and drives
 * Selection + Turn phases each round.  The print/pause flow now
 * matches the assignment PDF exactly.
 */
public final class CitadelsGame {

    /* ------------------------------------------------------------------ *
     *  Immutable configuration                                            *
     * ------------------------------------------------------------------ */
    private final List<Player> players;
    private final CommandHandler cli;
    private final Random rng = new Random();

    /* ------------------------------------------------------------------ *
     *  Mutable game-state                                                *
     * ------------------------------------------------------------------ */
    private Deck<DistrictCard> districtDeck;
    private final Deck<CharacterCard> characterDeck = buildCharacterDeck();

    private int crownedSeat;
    private int  roundNo     = 1;
    private GamePhase phase  = GamePhase.SELECTION;

    /* — per-round flags — */
    private final Set<Integer> killedRanks     = new HashSet<>();
    private int    robbedRank   = -1;
    private Player thiefPlayer  = null;
    private final Set<Player> bishopProtected  = new HashSet<>();
    private final Map<Player,Integer> builtThisTurn = new HashMap<>();

    /* ------------------------------------------------------------------ *
     *  Construction                                                       *
     * ------------------------------------------------------------------ */
    public CitadelsGame(int nPlayers, CommandHandler cli) {
        if (nPlayers < 4 || nPlayers > 7)
            throw new IllegalArgumentException("Players must be 4-7");
        this.cli = cli;

        /* players */
        players = new ArrayList<>();
        players.add(new HumanPlayer(0));
        for (int i = 1; i < nPlayers; i++) players.add(new AIPlayer(i));

        this.crownedSeat = rng.nextInt(nPlayers);

        /* district deck */
        districtDeck = new Deck<>(TSVLoader.loadDistrictDeck());
        districtDeck.shuffle(rng);

        /* initial deal (4 cards, 2 gold already) */
        for (Player p : players)
            for (int i = 0; i < 4; i++) p.addCardToHand(districtDeck.draw());
    }

    /* ================================================================ *
     *  Engine control                                                   *
     * ================================================================ */

    /**
     * Plays a round of the game
     */
    public void playRound() {

        /* crowned player / press-t gating (like PDF) */
        cli.println("Player " + (crownedSeat + 1) +
                " is the crowned player and goes first.");
        cli.println("Press t to process turns");

        selectionPhase(); //SELECTION PHASE
        turnPhase(); //TURN PHASE

        /* End-of-round line */
        cli.println("Everyone is done, new round!");
        waitForHumanT();

        //prepare for next round
        roundNo++;
        killedRanks.clear(); //clear the killed ranks
        robbedRank  = -1; //reset the robbed rank
        thiefPlayer = null; //reset the thief player
        bishopProtected.clear(); //clear the bishop protection
    }

    /**
     * Checks if the game is over
     * @return true if the game is over, false otherwise
     */
    public boolean isGameOver() {
        return players.stream().anyMatch(p -> p.getCity().size() >= 8);
    }

    /**
     * This method calculates the scores of all players and prints the results.
     */
    public void scoreAndPrintResults() {
        int firstDone = players.stream()
                .filter(p -> p.getCity().size() >= 8)
                .mapToInt(Player::getId).min().orElse(-1);

        Map<Player,Integer> score = ScoreCalculator.compute(players, firstDone);

        cli.println("\n=== FINAL SCORES ===");
        score.forEach((p,s) ->
                cli.println("Player " + (p.getId()+1) + ": " + s + " pts"));

        List<Player> winners = ScoreCalculator.winners(score);
        if (winners.size() == 1)
            cli.println("Congratulations Player " +
                    (winners.get(0).getId()+1) + "!");
        else
            cli.println("Tie game: break tie by highest last-round rank.");
    }

    /* ================================================================ *
     *  Selection phase                                                  *
     * ================================================================ */

    /**
     * Plays the selection phase
     */
    private void selectionPhase() {
        phase = GamePhase.SELECTION;

        cli.println("================================");
        cli.println("SELECTION PHASE");
        cli.println("================================");
        waitForHumanT();

        List<CharacterCard> tray = new ArrayList<>(characterDeck.asListView());
        Collections.shuffle(tray, rng); //shuffle the tray using the random number generator

        //face-up face down logic from game rules (the provided table)
        int faceUp = (players.size() == 4) ? 2 :
                     (players.size() == 5) ? 1 : 0;

        List<CharacterCard> pool, up, chars = new ArrayList<>();
        while (true) {
            pool = new ArrayList<>(tray);
            Collections.shuffle(pool, rng);

            up = new ArrayList<>(); //List to store the face-up cards during Selection Phase
            Iterator<CharacterCard> it = pool.iterator();
            for (int i = 0; i < faceUp && it.hasNext(); i++) up.add(it.next());
            it.next();          // one face-down irrespective of the number of players
            while (it.hasNext()) chars.add(it.next()); //add the remaining cards to the pool

            if (up.stream().anyMatch(King.class::isInstance)){ //if the face-up cards include King, clear the pool
                System.out.println("The King cannot be visibly removed, trying again..");
                chars.clear(); //clear the pool
            } 
            else break; //else break
        }

        for (CharacterCard c : up) {
            cli.println(c.getName() + " was removed.");
        }
        cli.println("A mystery character was removed."); //1 facedown card irrespective of no. of players
        waitForHumanT();

        int seat = crownedSeat; //index of the player who is crowned in current round
        List<CharacterCard> passing = new ArrayList<>(chars);

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(seat);

            CharacterCard chosen;
            if (p instanceof HumanPlayer) {
                cli.println("Choose your character. Available:");
                for (CharacterCard c : passing) cli.println(" - " + c.getName());
                chosen = humanChooseCharacter(passing);
            } else {
                chosen = passing.remove(0);
            }

            p.setCharacter(chosen);
            cli.println("Player " + (seat+1) + " chose a character.");

            seat = (seat + 1) % players.size(); // seat resets to 0 after last player
        }

    /* ================================================================ *
     *  Turn phase                                                      *
     * ================================================================ */
    
        cli.println("\nCharacter choosing is over, action round will now begin.");
        cli.println("================================");
        cli.println("TURN PHASE");
        cli.println("================================");
    }

    private CharacterCard humanChooseCharacter(List<CharacterCard> pool) {
        while (true) {
            String in = cli.prompt("> ").trim(); //trim means
            for (CharacterCard c : pool)
                if (c.getName().equalsIgnoreCase(in)) {
                    pool.remove(c); return c;
                }
            cli.println("Invalid name, try again.");
        }
    }

    private void turnPhase() {
        phase = GamePhase.TURN;

        for (int rank = 1; rank <= 8; rank++) {
            Player acting = findPlayerByRank(rank);

            cli.println(rank + ": " + rankName(rank));

            if (killedRanks.contains(rank)) {
                cli.println("Player " + (acting.getId()+1) + " loses their turn because they were assassinated.");
                waitForHumanT();
                continue;
            }
            if (acting == null) {
                cli.println("No one is the " + rankName(rank));
                waitForHumanT();
                continue;
            }

            cli.println("Player " + (acting.getId()+1) +
                        " is the " + rankName(rank));

            if (rank == 4) crownedSeat = acting.getId(); // King crown

            builtThisTurn.put(acting, 0); //reset the builtThisTurn map for the current player

            if (rank == robbedRank && thiefPlayer != null && thiefPlayer != acting) {
                int stolen = acting.getGold();
                acting.spendGold(stolen);
                thiefPlayer.gainGold(stolen);   
                cli.println("The Thief steals " + stolen + " gold.");
                waitForHumanT();
            }

            if (acting instanceof HumanPlayer) {
               cli.println("Your turn.\nCollect 2 gold or draw two cards and pick one [gold/cards]:");
            }

            acting.takeTurn(this); 

            //if debug is on and the player is an AI, print the hand of the player
            if (cli instanceof ConsoleHandler && ((ConsoleHandler)cli).isDebug()
                    && acting instanceof AIPlayer)
                cli.println("Debug: " + acting.getHand());

            waitForHumanT();
        }
    }

    /**
     * Waits for the human player to press t to continue
     */
    private void waitForHumanT() {
        //if (!(cli instanceof ConsoleHandler)) return;
        while (true) {
            String in = cli.prompt("> ").trim();
            if (in.equalsIgnoreCase("t")) return; //t was pressed
            if (in.equalsIgnoreCase("all")) { //"all" can be used at any time
                for (Player p : players) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Player ").append(p.getId() + 1);
                    
                    // Add "(you)" for the human player
                    if (p.getId() == 0) {
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
                    
                    cli.println(sb.toString());
                }
                continue;
            }

            if (in.equalsIgnoreCase("debug")) { //"debug" can be used at any time
                ((ConsoleHandler)cli).toggleDebug();
                continue;
            }
            cli.println("It is not your turn. Press t to continue with other player turns.");
        }
    }

    /* ================================================================ *
     *  Helper look-ups                                                 *
     * ================================================================ */

    /**
     * Finds the player with the given rank
     * @param rank the rank of the player to find
     * @return the player with the given rank
     */
    private Player findPlayerByRank(int rank) {
        return players.stream()
                .filter(p -> p.getCharacter() != null
                          && p.getCharacter().getRank() == rank)
                .findFirst().orElse(null);
    }

    /**
     * Returns the name of the character with the given rank
     * @param r the rank of the character
     * @return the name of the character
     */
    public static String rankName(int r) {
        switch (r) {
            case 1: return "Assassin";
            case 2: return "Thief";
            case 3: return "Magician";
            case 4: return "King";
            case 5: return "Bishop";
            case 6: return "Merchant";
            case 7: return "Architect";
            case 8: return "Warlord";
            default: return "?"; //should never happen for valid rank
        }
    }

    /**
     * Returns the CharacterCard prototype for the given rank (1-8).
     * @param rank the rank of the character
     * @return the CharacterCard prototype
     */
    public citadels.model.card.CharacterCard findCharacterCard(int rank) {
        for (citadels.model.card.CharacterCard c : characterDeck.asListView())
            if (c.getRank() == rank) return c;
        return null;        // should never happen for valid rank
    }


    /* ------------------------------------------------- *
     *  Public mutators used by CLI / abilities / save   *
     * ------------------------------------------------- */

    /* -- generic economy helpers -- */

    /**
     * Collects 2 gold for the player during beginning choice (draw card or collect gold)
     * @param p the player to collect the gold for
     */
    public void collectGold(Player p) {
        p.gainGold(2);
        cli.println("Player " + (p.getId()+1) + " collected 2 gold.");
    }

    /**
     * Gains gold for the player
     * @param p the player to gain the gold for
     * @param n the amount of gold to gain
     */
    public void gainGold(Player p, int n) { p.gainGold(n); }

    /**
     * Gains gold for the player based on the color of the district 
     * @param p the player to gain the gold for
     * @param color the color of the district
     */
    public void gainGoldForColor(Player p, DistrictColor color) {
        long n = p.getCity().stream()
                .filter(d -> d.getColor()==color || d.isSchoolOfMagic())
                .count();
        p.gainGold((int) n);
    }

    /**
     * Draws cards for the player
     * @param p the player to draw the cards for
     * @param n the number of cards to draw
     */
    public void drawCards(Player p, int n) {
        for (int i = 0; i < n && !districtDeck.isEmpty(); i++)
            p.addCardToHand(districtDeck.draw());
    }

    boolean picked_one = false; //flag to check if the player has already picked a card out of 2

    /**
     * Draws two cards and allows the player to pick one.
     * If the player is an AI, it will pick the card with the lower cost.
     * If the player is a human, it will prompt the player to pick one.
     * @param p the player to draw the cards for
     */
    public void drawTwoChoose(Player p) {
        if (districtDeck.size() < 2) { drawCards(p, 2); return; }
        //the 2 cards from which either 1 is drawn or if player has library, both are drawn
        DistrictCard a = districtDeck.draw();
        DistrictCard b = districtDeck.draw();

        // check if the player has a library in their city (library purple card effect)
        boolean hasLib = p.getCity().stream().anyMatch(DistrictCard::isLibrary);
        if (hasLib) {
            p.addCardToHand(a); p.addCardToHand(b);
            cli.println("Library effect: kept both.");
            return;
        }
        /* if the player is an AI, add the card with the lower cost to the player's hand
        and put the other card at the bottom of the deck*/
        if (p instanceof AIPlayer) {
            p.addCardToHand(a.getCost() >= b.getCost() ? a : b);
            districtDeck.putOnBottom(a.getCost() >= b.getCost() ? b : a);
            return;
        }

        cli.println("Pick one of the following cards: 1 or 2.\n1. " + a + "\n2. " + b);
        
        while (true) {
            String in = cli.prompt("> ").trim();
            //player can see info about the cards which they can pick
            if(in.startsWith("info")) {
                if(in.equals("info 1")) { CommandHandler.printDistrictInfo(a); }
                if(in.equals("info 2")) { CommandHandler.printDistrictInfo(b); }
                continue;
            }
            // pick the chosen card and put the other one at the bottom of the deck
            if (in.equals("1") && !picked_one) {p.addCardToHand(a); districtDeck.putOnBottom(b); picked_one = true; return;}
            if (in.equals("2") && !picked_one) {p.addCardToHand(b); districtDeck.putOnBottom(a); picked_one = true; return;}
            if (picked_one) { cli.println("You already picked one card!"); return; }
            cli.println("Invalid input, enter '1' or '2'.");
        }
    }

    /**
     * Builds a district for the player
     * @param p the player to build the district for
     * @param card the district card to build
     */
    public void buildDistrict(Player p, DistrictCard card) {
        //cannot build a district if the player already has it
        if (p.getCity().stream().anyMatch(d -> d.getName().equals(card.getName()))) {
            cli.println("You already have that district."); return;
        }
        //cannot afford the district
        if (!p.spendGold(card.getCost())) { cli.println("Cannot afford."); return; }
        //remove card from hand and add to city
        p.getHand().remove(card);
        p.addDistrictToCity(card);
        cli.println("Built " + card);
    }

    /* -- character-specific helpers -- */

    /**
     * Kills a character
     * @param rank the rank of the character to kill
     */
    public void killCharacter(int rank) { killedRanks.add(rank); }

    /**
     * Sets the target for the thief
     * @param thief the player who is the thief
     * @param rank the rank of the character to steal from
     */
    public void setRobTarget(Player thief, int rank) {
        thiefPlayer = thief; robbedRank = rank;
    }

    /**
     * Swaps the hands of two players (magician ability)
     * @param a the first player
     * @param b the second player
     */
    public void swapHands(Player a, Player b) {
        //store the hand of player a in a temporary list
        List<DistrictCard> tmp = new ArrayList<>(a.getHand());
        //clear the hand of player a and add the hand of player b to it
        a.getHand().clear(); a.getHand().addAll(b.getHand());
        //clear the hand of player b and add the temporary list to it
        b.getHand().clear(); b.getHand().addAll(tmp);
    }

    /**
     * Destroys a district
     * @param attacker the player who is attacking
     * @param victim the player who is being attacked
     * @param idx the index of the district to destroy
     */
    public void destroyDistrict(Player attacker, Player victim, int idx) {
        //invalid input
        if (idx<0||idx>=victim.getCity().size()) { cli.println("Invalid index. Enter a number between 1 and " + victim.getCity().size() + "."); return; }
        //warlord cannot destroy bishop's district
        if (bishopProtected.contains(victim)) { cli.println("Protected by Bishop."); return; }
        //get the district to destroy
        DistrictCard d = victim.getCity().get(idx);
        //calculate the cost to destroy the district (cost of district - 1)
        int cost = Math.max(0, d.getCost() - 1);
        //not enough gold
        if (!attacker.spendGold(cost)) { cli.println("Not enough gold."); return; }
        //remove the district from the city
        victim.getCity().remove(idx);
        cli.println("Destroyed " + d.getName() + " in Player " +
                (victim.getId()+1) + "'s city.");
    }

    /**
     * Takes the crown
     * @param p the player who is taking the crown
     */
    public void takeCrown(Player p) {
        crownedSeat = p.getId();
        cli.println("Player " + (p.getId()+1) + " receives the crown.");
    }

    /**
     * Sets the build limit for the player (for architect ability)
     * @param p the player to set the build limit for
     * @param limit the limit to set
     */
    public void setBuildLimit(Player p, int limit) {
        builtThisTurn.put(p, -limit); //-limit = may build up to limit
    }

    /**
     * Checks if the player is protected by the bishop
     * @param p the player to check
     * @return true if the player is protected by the bishop, false otherwise
     */
    public boolean isBishopProtected(Player p) { return bishopProtected.contains(p); }

    /**
     * Sets the bishop protection for the player if they chose bishop character
     * @param p the player to set the bishop protection for
     * @param on true if the player is protected by the bishop, false otherwise
     */
    public void setBishopProtection(Player p, boolean on) {
        if (on) bishopProtected.add(p); else bishopProtected.remove(p);
    }

    /* -- prompt helpers for Human players -- */

    /**
     * Prompts the player to select a character
     * @param actor the player who is selecting the character
     * @param from the starting rank of the character
     * @param to the ending rank of the character
     * @param verb the verb to use in the prompt
     * @return the rank of the selected character
     */
    public int promptCharacterSelection(Player actor, int from,int to,String verb) {
        //if the player is an AI, return a random character
        if (actor instanceof AIPlayer) return from + rng.nextInt(to-from+1);
        //if human player
        while (true) {
            String in = cli.prompt("Who do you want to "+verb+
                    "? Choose a character from "+from+"-"+to+":\n> ");
            try { int r=Integer.parseInt(in.trim());
                if (r>=from&&r<=to) return r;
            } catch (NumberFormatException ignored) { }
            cli.println("Please enter a number between "+from+" and "+to+".");
        }
    }

    /**
     * Prompts the player to select a player
     * @param actor the player who is selecting the player
     * @param q the question to prompt the player with
     * @return the player who is selected
     */
    public Player promptPlayerSelection(Player actor,String q) {
        //if the player is an AI, return a random player
        if (actor instanceof AIPlayer) return players.get(rng.nextInt(players.size()));
        //if human player
        while (true) {
            String in=cli.prompt(q+" (1-"+players.size()+"):\n> ");

            try { int seat=Integer.parseInt(in.trim())-1;
                if (seat>=0&&seat<players.size()) return players.get(seat);
            } catch (NumberFormatException ignored) { }
            cli.println("Invalid player number.");
        }
    }

    /**
     * Prompts the player who chose Warlord to select a district
     * @param victim the player who is being selected from
     * @param q the question to prompt the player with
     * @return the index of the selected district
     */
    public int promptDistrictSelection(Player victim,String q) {
        if (victim.getCity().isEmpty()) return -1;
        if (victim instanceof AIPlayer) return 0;
        cli.println("Victim city:");
        for (int i=0;i<victim.getCity().size();i++)
            cli.println((i+1)+". "+victim.getCity().get(i));
        while (true) {
            String in=cli.prompt(q+" (1-"+victim.getCity().size()+"):\n> ");
            try { int idx=Integer.parseInt(in.trim())-1;
                if (idx>=0&&idx<victim.getCity().size()) return idx;
            } catch (NumberFormatException ignored) { }
            cli.println("Invalid index.");
        }
    }

    /**
     * Prompts the player to discard cards (Magician ability)
     * @param p the player who is discarding the cards
     * @param msg the message to prompt the player with
     * @return the number of cards discarded
     */
    public int promptAndDiscardCards(Player p,String msg){
        if(p instanceof AIPlayer)return 0;
        cli.println(msg+" (comma-separated hand indexes, blank cancels):");
        for (int i=0;i<p.getHand().size();i++)
            cli.println((i+1)+". "+p.getHand().get(i));
        String in=cli.prompt("> ").trim();
        if(in.isEmpty())return 0;
        int disc=0;
        for(String s:in.split(",")){
            try{
                int idx=Integer.parseInt(s.trim())-1;
                if(idx>=0&&idx<p.getHand().size()){ p.getHand().remove(idx); disc++; }
            }catch(NumberFormatException ignore){}
        }
        return disc;
    }


    /**
     * Returns the rank of the robbed character
     * @return the rank of the robbed character
     */
    public int getRobbedRank(){ return robbedRank; }

    /**
     * Returns the set of killed ranks
     * @return the set of killed ranks
     */
    public Set<Integer> getKilledRanks(){ return new HashSet<>(killedRanks); }

    /**
     * Returns the player protected by the bishop
     * @return the player protected by the bishop
     */
    public Set<Player> getBishopProtected(){ return new HashSet<>(bishopProtected); }

    /**
     * Sets the robbed rank
     * @param r the rank of the robbed character
     */
    public void setRobbedRank(int r){ robbedRank=r; }

    /**
     * Sets the killed ranks
     * @param ks the set of killed ranks
     */
    public void setKilledRanks(Set<Integer> ks){ killedRanks.clear(); killedRanks.addAll(ks); }


    public void setBishopProtected(Set<Player> ps){ bishopProtected.clear(); bishopProtected.addAll(ps); }

    /**
     * Returns the player at the given seat
     * @param seat the seat of the player
     * @return the player at the given seat
     */
    public Player getPlayer(int seat){ return players.get(seat); }

    /**
     * Returns the list of players
     * @return the list of players
     */
    public java.util.List<Player> getPlayers() {return java.util.Collections.unmodifiableList(players);}

    /**
     * Returns the command handler
     * @return the command handler
     */
    public citadels.cli.CommandHandler cli() {return cli;}

    /**
     * Returns the command handler
     * @return the command handler
     */

    /* ------------------------------------------------- *
     *  Save / load round + deck helpers for GameState   *
     * ------------------------------------------------- */

    /**
     * Returns the current round number
     * @return the current round number
     */
    public int getRound() { return roundNo; }

    /**
     * Returns the crowned seat
     * @return the crowned seat
     */
    public int getCrownedSeat() { return crownedSeat; }

    /** 
     * Returns the list of district deck names
     * @return the list of district deck names
     */
    public java.util.List<String> getDistrictDeckNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (DistrictCard d : districtDeck.asListView()) names.add(d.getName());
        return names;
    }

    /**
     * Sets the round number
     * @param r the round number
     */
    public void setRound(int r) { this.roundNo = r; }

    /**
     * Sets the crowned seat
     * @param seat the seat of the crowned player
     */
    public void setCrownedSeat(int seat) { this.crownedSeat = seat; }

    /**
     * Replaces the current deck with an ordered list of cards
     * @param ordered the ordered list of cards
     */
    public void resetDistrictDeck(java.util.List<citadels.model.card.DistrictCard> ordered) {
        this.districtDeck = new Deck<>(ordered);
    }


    /* ================================================================ *
     *  Character deck builder                                          *
     * ================================================================ */
    /**
     * Builds the character deck
     * @return the character deck
     */
    private Deck<CharacterCard> buildCharacterDeck() {
        /*deck is a versatile data structure that allows for the insertion
        and deletion of elements from both ends.*/
        return new Deck<>(Arrays.asList(
                new Assassin(), new Thief(), new Magician(), new King(),
                new Bishop(), new Merchant(), new Architect(), new Warlord()
        ));
    }
}
