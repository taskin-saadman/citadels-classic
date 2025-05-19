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
    public void playRound() {

        /* crowned player / press-t gating (like PDF) */
        cli.println("Player " + (crownedSeat + 1) +
                " is the crowned player and goes first.");
        cli.println("Press t to process turns");

        selectionPhase();
        turnPhase();

        /* End-of-round line */
        cli.println("Everyone is done, new round!");
        waitForHumanT();

        roundNo++;
        killedRanks.clear();
        robbedRank  = -1;
        thiefPlayer = null;
        bishopProtected.clear();
    }

    public boolean isGameOver() {
        return players.stream().anyMatch(p -> p.getCity().size() >= 8);
    }

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
            cli.println("Tie game — break tie by highest last-round rank.");
    }

    /* ================================================================ *
     *  Selection phase                                                  *
     * ================================================================ */
    private void selectionPhase() {
        phase = GamePhase.SELECTION;

        cli.println("================================");
        cli.println("SELECTION PHASE");
        cli.println("================================");
        waitForHumanT();

        List<CharacterCard> tray = new ArrayList<>(characterDeck.asListView());
        Collections.shuffle(tray, rng);

        int faceUp = (players.size() == 4) ? 2 :
                     (players.size() == 5) ? 1 : 0;

        List<CharacterCard> pool, up, chars = new ArrayList<>();
        while (true) {
            pool = new ArrayList<>(tray);
            Collections.shuffle(pool, rng);

            up = new ArrayList<>();
            Iterator<CharacterCard> it = pool.iterator();
            for (int i = 0; i < faceUp && it.hasNext(); i++) up.add(it.next());
            it.next();                              // one face-down
            while (it.hasNext()) chars.add(it.next());

            if (up.stream().anyMatch(King.class::isInstance)) chars.clear();
            else break;
        }

        for (CharacterCard c : up) {
            cli.println(c.getName() + " was removed.");
        }
        cli.println("A mystery character was removed.");
        waitForHumanT();

        int seat = crownedSeat;
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
            waitForHumanT();

            seat = (seat + 1) % players.size();
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
            String in = cli.prompt("> ").trim();
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
            waitForHumanT();

            if (killedRanks.contains(rank)) {
                cli.println("Character was killed - skipping turn.");
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
            waitForHumanT();

            if (rank == 4) crownedSeat = acting.getId(); // King crown

            builtThisTurn.put(acting, 0);

            if (rank == robbedRank && thiefPlayer != null && thiefPlayer != acting) {
                int stolen = acting.getGold();
                acting.spendGold(stolen);
                thiefPlayer.gainGold(stolen);
                cli.println("The Thief steals " + stolen + " gold.");
                waitForHumanT();
            }

            if (acting instanceof HumanPlayer) {
                cli.println("Your turn.");
                waitForHumanT();
            }

            acting.takeTurn(this);

            if (cli instanceof ConsoleHandler && ((ConsoleHandler)cli).isDebug()
                    && acting instanceof AIPlayer)
                cli.println("Debug: " + acting.getHand());

            waitForHumanT();
        }
    }

    /* ================================================================ *
     *  Wait helper (PDF-style gating)                                  *
     * ================================================================ */
    private void waitForHumanT() {
        if (!(cli instanceof ConsoleHandler)) return;
        while (true) {
            String in = cli.prompt("> ").trim();
            if (in.equalsIgnoreCase("t")) return;
            cli.println("It is not your turn. Press t to continue with other player turns.");
        }
    }

    /* ================================================================ *
     *  Helper look-ups                                                 *
     * ================================================================ */
    private Player findPlayerByRank(int rank) {
        return players.stream()
                .filter(p -> p.getCharacter() != null
                          && p.getCharacter().getRank() == rank)
                .findFirst().orElse(null);
    }

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
            default: return "?";
        }
    }

    /** Returns the CharacterCard prototype for the given rank (1-8). */
    public citadels.model.card.CharacterCard findCharacterCard(int rank) {
        for (citadels.model.card.CharacterCard c : characterDeck.asListView())
            if (c.getRank() == rank) return c;
        return null;        // should never happen for valid rank
    }


    /* ------------------------------------------------- *
     *  Public mutators used by CLI / abilities / save   *
     * ------------------------------------------------- */

    /* -- generic economy helpers -- */
    public void collectGold(Player p) {
        p.gainGold(2);
        cli.println("Player " + (p.getId()+1) + " collected 2 gold.");
    }

    public void gainGold(Player p, int n) { p.gainGold(n); }

    public void gainGoldForColor(Player p, DistrictColor color) {
        long n = p.getCity().stream()
                .filter(d -> d.getColor()==color || d.isSchoolOfMagic())
                .count();
        p.gainGold((int) n);
    }

    public void drawCards(Player p, int n) {
        for (int i = 0; i < n && !districtDeck.isEmpty(); i++)
            p.addCardToHand(districtDeck.draw());
    }

    public void drawTwoChoose(Player p) {
        if (districtDeck.size() < 2) { drawCards(p, 2); return; }
        DistrictCard a = districtDeck.draw();
        DistrictCard b = districtDeck.draw();

        boolean hasLib = p.getCity().stream().anyMatch(DistrictCard::isLibrary);
        if (hasLib) {
            p.addCardToHand(a); p.addCardToHand(b);
            cli.println("Library effect: kept both.");
            return;
        }

        if (p instanceof AIPlayer) {
            p.addCardToHand(a.getCost() >= b.getCost() ? a : b);
            districtDeck.putOnBottom(a.getCost() >= b.getCost() ? b : a);
            return;
        }

        cli.println("Pick one of the following cards: 'collect card <1|2>'.");
        cli.println("1. " + a); cli.println("2. " + b);
        while (true) {
            String in = cli.prompt("> ").trim();
            if (in.equalsIgnoreCase("collect card 1")) {
                p.addCardToHand(a); districtDeck.putOnBottom(b); break;
            }
            if (in.equalsIgnoreCase("collect card 2")) {
                p.addCardToHand(b); districtDeck.putOnBottom(a); break;
            }
            cli.println("Type 'collect card 1' or 'collect card 2'");
        }
    }

    public void buildDistrict(Player p, DistrictCard card) {
        if (p.getCity().stream().anyMatch(d -> d.getName().equals(card.getName()))) {
            cli.println("You already have that district."); return;
        }
        if (!p.spendGold(card.getCost())) { cli.println("Cannot afford."); return; }
        p.getHand().remove(card);
        p.addDistrictToCity(card);
        cli.println("Built " + card);
    }

    /* -- character-specific helpers -- */
    public void killCharacter(int rank) { killedRanks.add(rank); }
    public void setRobTarget(Player thief, int rank) {
        thiefPlayer = thief; robbedRank = rank;
    }
    public void swapHands(Player a, Player b) {
        List<DistrictCard> tmp = new ArrayList<>(a.getHand());
        a.getHand().clear(); a.getHand().addAll(b.getHand());
        b.getHand().clear(); b.getHand().addAll(tmp);
    }
    public void destroyDistrict(Player attacker, Player victim, int idx) {
        if (idx<0||idx>=victim.getCity().size()) return;
        if (bishopProtected.contains(victim)) { cli.println("Protected by Bishop."); return; }
        DistrictCard d = victim.getCity().get(idx);
        int cost = Math.max(0, d.getCost() - 1);
        if (!attacker.spendGold(cost)) { cli.println("Not enough gold."); return; }
        victim.getCity().remove(idx);
        cli.println("Destroyed " + d.getName() + " in Player " +
                (victim.getId()+1) + "'s city.");
    }
    public void takeCrown(Player p) {
        crownedSeat = p.getId();
        cli.println("Player " + (p.getId()+1) + " receives the crown.");
    }
    public void setBuildLimit(Player p, int limit) {
        builtThisTurn.put(p, -limit);             // negative = may build up to N
    }
    public boolean isBishopProtected(Player p) { return bishopProtected.contains(p); }
    public void setBishopProtection(Player p, boolean on) {
        if (on) bishopProtected.add(p); else bishopProtected.remove(p);
    }

    /* -- prompt helpers for Human players -- */
    public int promptCharacterSelection(Player actor, int from,int to,String verb) {
        if (actor instanceof AIPlayer) return from + rng.nextInt(to-from+1);
        while (true) {
            String in = cli.prompt("Who do you want to "+verb+
                    "? Choose a character from "+from+"-"+to+":\n> ");
            try { int r=Integer.parseInt(in.trim());
                if (r>=from&&r<=to) return r;
            } catch (NumberFormatException ignored) { }
            cli.println("Please enter a number between "+from+" and "+to+".");
        }
    }
    public Player promptPlayerSelection(Player actor,String q) {
        if (actor instanceof AIPlayer) return players.get(rng.nextInt(players.size()));
        while (true) {
            String in=cli.prompt(q+" (1-"+players.size()+"):\n> ");
            try { int seat=Integer.parseInt(in.trim())-1;
                if (seat>=0&&seat<players.size()) return players.get(seat);
            } catch (NumberFormatException ignored) { }
            cli.println("Invalid player number.");
        }
    }
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

    /* getters for GameState */
    public int getRobbedRank(){ return robbedRank; }
    public Set<Integer> getKilledRanks(){ return new HashSet<>(killedRanks); }
    public Set<Player> getBishopProtected(){ return new HashSet<>(bishopProtected); }
    public void setRobbedRank(int r){ robbedRank=r; }
    public void setKilledRanks(Set<Integer> ks){ killedRanks.clear(); killedRanks.addAll(ks); }
    public void setBishopProtected(Set<Player> ps){ bishopProtected.clear(); bishopProtected.addAll(ps); }
    public Player getPlayer(int seat){ return players.get(seat); }
    public java.util.List<Player> getPlayers() {return java.util.Collections.unmodifiableList(players);}
    public citadels.cli.CommandHandler cli() {return cli;}

        /* ------------------------------------------------- *
     *  Save / load round + deck helpers for GameState   *
     * ------------------------------------------------- */

    /** Current round number (1-based). */
    public int getRound() { return roundNo; }

    /** Crowned player’s seat index. */
    public int getCrownedSeat() { return crownedSeat; }

    /** Ordered list of the remaining district deck for save. */
    public java.util.List<String> getDistrictDeckNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (DistrictCard d : districtDeck.asListView()) names.add(d.getName());
        return names;
    }

    /** For loading a save: set round number. */
    public void setRound(int r) { this.roundNo = r; }

    /** For loading a save: set who holds the crown. */
    public void setCrownedSeat(int seat) { this.crownedSeat = seat; }

    /** Replace the current deck with an ordered list of cards (for load). */
    public void resetDistrictDeck(java.util.List<citadels.model.card.DistrictCard> ordered) {
        this.districtDeck = new Deck<>(ordered);
    }


    /* ================================================================ *
     *  Character deck builder                                          *
     * ================================================================ */
    private Deck<CharacterCard> buildCharacterDeck() {
        return new Deck<>(Arrays.asList(
                new Assassin(), new Thief(), new Magician(), new King(),
                new Bishop(), new Merchant(), new Architect(), new Warlord()
        ));
    }
}
