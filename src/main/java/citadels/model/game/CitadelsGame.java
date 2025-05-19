package citadels.model.game;

import citadels.cli.*;
import citadels.model.card.*;
import citadels.model.character.*;
import citadels.model.player.*;
import citadels.util.TSVLoader;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Central game engine for Citadels Classic.
 * <p>Now includes edge-case rule logic:
 * <ul>
 *   <li>Bishop-protected cities cannot be destroyed by the Warlord</li>
 *   <li>Architect can build up to three districts (per-turn counter)</li>
 *   <li>Crown automatically passes to the King even if assassinated</li>
 * </ul>
 */
public final class CitadelsGame {

    /* ------------------------------------------------- *
     *  Construction-time invariants                     *
     * ------------------------------------------------- */
    private final List<Player> players;
    private final CommandHandler cli;
    private final Random rng = new Random();

    /* ------------------------------------------------- *
     *  Mutable state                                    *
     * ------------------------------------------------- */
    private Deck<DistrictCard> districtDeck;
    private final Deck<CharacterCard> characterDeck = buildCharacterDeck();

    private int       crownedSeat = 0;          // seat index
    private GamePhase phase       = GamePhase.SELECTION;
    private int       roundNo     = 1;

    /* ---- per-round flags ---- */
    private final Set<Integer> killedRanks   = new HashSet<>();
    private int                robbedRank    = -1;
    private Player             thiefPlayer   = null;
    private final Set<Player>  bishopProtected = new HashSet<>();
    private final Map<Player,Integer> builtThisTurn = new HashMap<>();

    /* ------------------------------------------------- *
     *  Constructor                                      *
     * ------------------------------------------------- */
    public CitadelsGame(int nPlayers, CommandHandler cli) {
        if (nPlayers < 4 || nPlayers > 7)
            throw new IllegalArgumentException("Players must be 4-7");
        this.cli = cli;

        /* players */
        players = new ArrayList<>();
        players.add(new HumanPlayer(0));
        for (int i = 1; i < nPlayers; i++) players.add(new AIPlayer(i));

        /* district deck */
        districtDeck = new Deck<>(TSVLoader.loadDistrictDeck());
        districtDeck.shuffle(rng);

        /* initial deal */
        for (Player p : players)
            for (int i = 0; i < 4; i++) p.addCardToHand(districtDeck.draw());
    }

    /* ------------------------------------------------- *
     *  Round driver                                     *
     * ------------------------------------------------- */
    public void playRound() {
        cli.println("\n=== ROUND " + roundNo +
                    " (Crown: Player " + (crownedSeat + 1) + ") ===");

        selectionPhase();
        turnPhase();

        roundNo++;
        killedRanks.clear();
        robbedRank = -1;
        thiefPlayer = null;
        bishopProtected.clear();
        builtThisTurn.clear();
    }

    public boolean isGameOver() {
        return players.stream().anyMatch(p -> p.getCity().size() >= 8);
    }

    /* ------------------------------------------------- *
     *  Scoring                                          *
     * ------------------------------------------------- */
    public void scoreAndPrintResults() {
        int firstDone = players.stream()
                               .filter(p -> p.getCity().size() >= 8)
                               .mapToInt(Player::getId)
                               .min().orElse(-1);

        var scores = ScoreCalculator.compute(players, firstDone);

        cli.println("\n=== FINAL SCORES ===");
        scores.forEach((p, s) ->
                cli.println("Player " + (p.getId() + 1) + ": " + s + " pts"));

        var winners = ScoreCalculator.winners(scores);
        if (winners.size() == 1)
            cli.println("🥇 Congratulations Player "
                        + (winners.get(0).getId() + 1) + "!");
        else
            cli.println("Tie! Highest-rank character breaks tie manually.");
    }

    /* ------------------------------------------------- *
     *  Accessors for save/load                          *
     * ------------------------------------------------- */
    int  getRound()                 { return roundNo; }
    void setRound(int r)            { roundNo = r; }

    int  getCrownedSeat()           { return crownedSeat; }
    void setCrownedSeat(int s)      { crownedSeat = s; }

    public List<Player> getPlayers(){ return Collections.unmodifiableList(players);}

        /* ------------------------------------------------- *
     *  Extra helper accessors for CLI / tests           *
     * ------------------------------------------------- */

    /** Direct seat lookup (seat numbers are 0-based internally). */
    public Player getPlayer(int seatIndex) {
        return players.get(seatIndex);
    }

    /** Return the CharacterCard currently assigned to the given rank, or null. */
    public CharacterCard findCharacterCard(int rank) {
        Player p = findPlayerByRank(rank);    // existing private helper
        return (p == null) ? null : p.getCharacter();
    }

    
    List<String> getDistrictDeckNames() {
        return districtDeck.asListView().stream().map(Card::getName).collect(Collectors.toList());
    }
    void resetDistrictDeck(List<DistrictCard> ordered) {
        districtDeck = new Deck<>(ordered);
    }

    public CommandHandler cli() { return cli; }

    /* ------------------------------------------------- *
     *  Selection phase (unchanged core)                 *
     * ------------------------------------------------- */
    private void selectionPhase() {
        phase = GamePhase.SELECTION;
        cli.println("\n--- SELECTION PHASE ---");

        List<CharacterCard> tray = new ArrayList<>(characterDeck.asListView());
        Collections.shuffle(tray, rng);

        int n = players.size();
        int faceUp = (n == 4) ? 2 : (n == 5) ? 1 : 0;

        List<CharacterCard> pool, up, chars = new ArrayList<>();
        while (true) {                  // repeat until King not face-up
            pool = new ArrayList<>(tray);
            Collections.shuffle(pool, rng);

            up   = new ArrayList<>();
            Iterator<CharacterCard> it = pool.iterator();
            for (int i = 0; i < faceUp && it.hasNext(); i++) up.add(it.next());
            it.next();                  // one face-down discard
            while (it.hasNext()) chars.add(it.next());

            if (up.stream().anyMatch(King.class::isInstance)) chars.clear();
            else break;
        }

        up.forEach(c -> cli.println(c.getName() + " was removed."));
        cli.println("A mystery character was removed.");

        int seat = crownedSeat;
        List<CharacterCard> passing = new ArrayList<>(chars);

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(seat);
            CharacterCard chosen = p instanceof HumanPlayer
                    ? humanChooseCharacter(passing)
                    : passing.remove(0);
            p.setCharacter(chosen);
            cli.println("Player " + (seat + 1) + " chose a character.");
            seat = (seat + 1) % players.size();
        }
    }

    private CharacterCard humanChooseCharacter(List<CharacterCard> pool) {
        cli.println("Choose your character. Available:");
        pool.forEach(c -> cli.println(" - " + c.getName()));
        while (true) {
            String in = cli.prompt("> ").trim();
            for (CharacterCard c : pool)
                if (c.getName().equalsIgnoreCase(in)) {
                    pool.remove(c); return c;
                }
            cli.println("Invalid name, try again.");
        }
    }

    /* ------------------------------------------------- *
     *  Turn phase                                       *
     * ------------------------------------------------- */
    private void turnPhase() {
        phase = GamePhase.TURN;
        cli.println("\n--- TURN PHASE ---");

        for (int rank = 1; rank <= 8; rank++) {
            Player acting = findPlayerByRank(rank);
            cli.println(rank + ": " + rankName(rank));

            if (killedRanks.contains(rank)) { cli.println("Killed — skip."); continue; }
            if (acting == null)              { cli.println("No one is " + rankName(rank)); continue; }

            /* King crown persistence (even if assassinated) */
            if (rank == 4) crownedSeat = acting.getId();

            builtThisTurn.put(acting, 0);    // reset per-turn counter

            /* Robbery resolution */
            if (rank == robbedRank && thiefPlayer != null && thiefPlayer != acting) {
                int stolen = acting.getGold();
                acting.spendGold(stolen);
                thiefPlayer.gainGold(stolen);
                cli.println("The Thief steals " + stolen + " gold.");
            }

            if (acting instanceof HumanPlayer) cli.println("Your turn.");
            acting.takeTurn(this);           // ability + actions
            if (cli instanceof ConsoleHandler && ((ConsoleHandler)cli).isDebug()
                                && acting instanceof AIPlayer) {
                cli.println("Debug: " + acting.getHand());
            }

        }
    }

    private Player findPlayerByRank(int rank) {
        return players.stream()
                      .filter(p -> p.getCharacter() != null
                                && p.getCharacter().getRank() == rank)
                      .findFirst().orElse(null);
    }

    private static String rankName(int r) {
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

    /* ------------------------------------------------- *
     *  Prompt helpers (same simple stubs as before)     *
     * ------------------------------------------------- */
    public int promptCharacterSelection(Player p,int from,int to,String q){
        if(p instanceof AIPlayer) return from;
        while(true){
            try{ int v=Integer.parseInt(cli.prompt(q+"\n> ").trim());
                 if(v>=from&&v<=to) return v; }catch(Exception ignore){}
            cli.println("Invalid number.");
        }
    }
    public Player promptPlayerSelection(Player p,String q){
        if(p instanceof AIPlayer) return players.get(rng.nextInt(players.size()));
        while(true){
            try{ int idx=Integer.parseInt(cli.prompt(q+" (1-"+players.size()+"):\n> ").trim())-1;
                 return players.get(idx);}catch(Exception ignore){cli.println("Invalid.");}
        }
    }
    public int promptDistrictSelection(Player v,String q){
        if(v.getCity().isEmpty()) return -1;
        if(v instanceof AIPlayer) return 0;
        for(int i=0;i<v.getCity().size();i++)
            cli.println((i+1)+". "+v.getCity().get(i));
        while(true){
            try{ int idx=Integer.parseInt(cli.prompt(q+"\n> ").trim())-1;
                 if(idx>=0&&idx<v.getCity().size()) return idx;}catch(Exception ignore){}
            cli.println("Invalid index.");
        }
    }
    public int promptAndDiscardCards(Player p,String q){ return 0; }

    /** Query method used by AI to see if a player is Bishop-protected. */
    public boolean isBishopProtected(Player p) {
        return bishopProtected.contains(p);
    }

    /* ------------------------------------------------- *
     *  Mutators / rule-logic                            *
     * ------------------------------------------------- */
    public void killCharacter(int rank) {
        killedRanks.add(rank);
        if (rank == 5) {                      // Bishop killed → lose protection
            Player bishop = findPlayerByRank(5);
            if (bishop != null) bishopProtected.remove(bishop);
        }
    }
    public void setRobTarget(Player thief,int victimRank){
        thiefPlayer=thief; robbedRank=victimRank;
    }
    public void swapHands(Player a,Player b){
        List<DistrictCard> tmp=new ArrayList<>(a.getHand());
        a.getHand().clear(); a.getHand().addAll(b.getHand());
        b.getHand().clear(); b.getHand().addAll(tmp);
    }

    public void drawCards(Player p,int n){
        for(int i=0;i<n && !districtDeck.isEmpty();i++)
            p.addCardToHand(districtDeck.draw());
    }
    /** Draw 2 cards; owner keeps both if Library, otherwise keep-one-discard-one. */
    public void drawTwoChoose(Player p) {
        if (districtDeck.size() < 2) { drawCards(p, 2); return; }

        DistrictCard a = districtDeck.draw();
        DistrictCard b = districtDeck.draw();

        if (p.getCity().stream().anyMatch(card -> card.isLibrary())) {
            p.addCardToHand(a); p.addCardToHand(b);
            cli.println("Library effect: kept both cards.");
            return;
        }

        // AI keeps highest-cost; human handled in CLI later
        if (p instanceof AIPlayer) {
            DistrictCard keep = (a.getCost() >= b.getCost()) ? a : b;
            DistrictCard discard = (keep == a) ? b : a;
            p.addCardToHand(keep);
            districtDeck.putOnBottom(discard);
            return;
        }

        // human → show both, ask choice
        cli.println("Pick one of the following cards: 'collect card <1|2>'.");
        cli.println("1. " + a);
        cli.println("2. " + b);
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

    public void collectGold(Player p){
        p.gainGold(2); cli.println("Player "+(p.getId()+1)+" collected 2 gold.");
    }
    public void gainGold(Player p,int n){ p.gainGold(n); }
    public void gainGoldForColor(Player p, DistrictColor color) {
        long count = p.getCity().stream()
                  .filter(c -> c.getColor() == color || c.isSchoolOfMagic())
                  .count();
        p.gainGold((int) count);
    }


    /* ----- build with Architect limit & duplicates check ----- */
    public void buildDistrict(Player p, DistrictCard card) {
        int built = builtThisTurn.getOrDefault(p, 0);
        if (built >= p.getBuildLimit()) { cli.println("Build limit reached."); return; }
        if (p.cityContains(card.getName())) { cli.println("Duplicate district."); return; }
        if (!p.spendGold(card.getCost()))   { cli.println("You cannot afford it."); return; }

        p.getHand().remove(card);
        p.addDistrictToCity(card);
        builtThisTurn.put(p, built + 1);
        cli.println("Built " + card);
    }

    public void takeCrown(Player p){ crownedSeat = p.getId(); }

    public void setBishopProtection(Player p, boolean on){
        if(on) bishopProtected.add(p); else bishopProtected.remove(p);
    }
    public void setBuildLimit(Player p,int max){ p.setBuildLimit(max); }

    /* ----- warlord destruction with bishop & completion checks ----- */
    public void destroyDistrict(Player attacker, Player victim,int idx){
        if(victim.getCity().size()>=8){
            cli.println("Cannot destroy a completed city."); return;
        }
        if(bishopProtected.contains(victim)){
            cli.println("Bishop protects the city."); return;
        }
        if(idx<0||idx>=victim.getCity().size()) return;
        DistrictCard card=victim.getCity().get(idx);
        int cost=Math.max(0, card.getCost()-1);
        if(attacker.getGold()<cost){
            cli.println("Not enough gold."); return;
        }
        attacker.spendGold(cost);
        victim.getCity().remove(idx);
        cli.println("Destroyed " + card.getName() +
                    " in Player " + (victim.getId()+1) + "'s city.");
    }

    /* ------------------------------------------------- *
     *  Utilities                                        *
     * ------------------------------------------------- */
    private Deck<CharacterCard> buildCharacterDeck() {
        return new Deck<>(List.of(
                new Assassin(), new Thief(), new Magician(), new King(),
                new Bishop(),  new Merchant(), new Architect(), new Warlord()
        ));
    }

    /* ---------- state needed for save/load ---------- */
public Set<Integer> getKilledRanks()          { return new HashSet<>(killedRanks); }
public void         setKilledRanks(Set<Integer> ks) { killedRanks.clear(); killedRanks.addAll(ks); }

public int  getRobbedRank()                   { return robbedRank; }
public void setRobbedRank(int r)              { robbedRank = r; }

public Set<Player> getBishopProtected()       { return new HashSet<>(bishopProtected); }
public void        setBishopProtected(Set<Player> ps) {
    bishopProtected.clear(); bishopProtected.addAll(ps);
}

}
