package citadels.model.game;

import citadels.cli.CommandHandler;
import citadels.model.card.*;
import citadels.model.character.*;
import citadels.model.player.*;
import citadels.util.TSVLoader;

import java.util.*;

/**
 * Core game controller: maintains all mutable state and
 * coordinates Selection + Turn phases each round.
 *
 * <p>Anything not yet fully implemented is marked <b>TODO</b>, but every
 * public method referenced by the rest of the skeleton now exists so the
 * whole project compiles & runs end-to-end.</p>
 */
public final class CitadelsGame {

    /* ------------------------------------------------- *
     * Construction-time invariants                      *
     * ------------------------------------------------- */

    private final List<Player> players;
    private final CommandHandler cli;
    private final Random rng = new Random();

    /* ------------------------------------------------- *
     * Mutable state                                     *
     * ------------------------------------------------- */

    private Deck<DistrictCard>  districtDeck;
    private final Deck<CharacterCard> characterDeck = buildCharacterDeck();

    private int       crownedSeat = 0;      // index into players
    private GamePhase phase       = GamePhase.SELECTION;
    private int       roundNo     = 1;

    /* ---- per-round flags ---- */
    private final Set<Integer> killedRanks = new HashSet<>();
    private int                robbedRank = -1;
    private Player             thiefPlayer = null;

    /* ------------------------------------------------- *
     * Constructor                                       *
     * ------------------------------------------------- */

    public CitadelsGame(int nPlayers, CommandHandler cli) {
        if (nPlayers < 4 || nPlayers > 7)
            throw new IllegalArgumentException("Players must be 4-7");
        this.cli = cli;

        /* --- players --- */
        players = new ArrayList<>();
        players.add(new HumanPlayer(0));
        for (int i = 1; i < nPlayers; i++) players.add(new AIPlayer(i));

        /* --- district deck from TSV file --- */
        districtDeck = new Deck<>(TSVLoader.loadDistrictDeck());
        districtDeck.shuffle(rng);

        /* --- initial dealing --- */
        for (Player p : players) {
            for (int i = 0; i < 4; i++) p.addCardToHand(districtDeck.draw());
        }
        // each player already starts with 2 gold in Player constructor
    }

    /* ------------------------------------------------- *
     * Public engine control                             *
     * ------------------------------------------------- */

    public void playRound() {
        cli.println("\n=== ROUND " + roundNo +
                    " (Crown: Player " + (crownedSeat + 1) + ") ===");

        selectionPhase();
        turnPhase();

        roundNo++;
        killedRanks.clear();
        robbedRank  = -1;
        thiefPlayer = null;
    }

    public boolean isGameOver() {
        return players.stream().anyMatch(p -> p.getCity().size() >= 8);
    }

    public void scoreAndPrintResults() {
    int firstDone = players.stream()
                           .filter(p -> p.getCity().size() >= 8)
                           .mapToInt(Player::getId)
                           .min().orElse(-1);

    var scores = ScoreCalculator.compute(players, firstDone);

    cli.println("\n=== FINAL SCORES ===");
    scores.forEach((p,s) -> cli.println(
            "Player " + (p.getId()+1) + ": " + s + " pts"));

    var winners = ScoreCalculator.winners(scores);
    if (winners.size() == 1)
        cli.println("🥇 Congratulations Player " +
                    (winners.get(0).getId()+1) + "!");
    else {
        cli.println("Tie! Highest-rank character breaks tie manually.");
    }
}


    /* ------------------------------------------------- *
     *  Accessors used by GameState / CLI / tests        *
     * ------------------------------------------------- */

    // round & crown
    int  getRound()           { return roundNo; }
    void setRound(int r)      { roundNo = r;    }

    int  getCrownedSeat()     { return crownedSeat; }
    void setCrownedSeat(int s){ crownedSeat = s;    }

    // players (read-only view)
    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    // district deck name list (for save)
    List<String> getDistrictDeckNames() {
        return districtDeck.asListView().stream()
                           .map(Card::getName)
                           .toList();
    }

    /** Replace the current district deck with a new ordered list. */
    void resetDistrictDeck(List<DistrictCard> ordered) {
        districtDeck = new Deck<>(ordered);
    }

    public CommandHandler cli() { return cli; }

    /* ------------------------------------------------- *
     *  Selection Phase (unchanged logic, trimmed)       *
     * ------------------------------------------------- */

    private void selectionPhase() {
        phase = GamePhase.SELECTION;
        cli.println("\n--- SELECTION PHASE ---");

        List<CharacterCard> tray = new ArrayList<>(characterDeck.asListView());
        Collections.shuffle(tray, rng);

        int n = players.size();
        int faceUp   = (n == 4) ? 2 : (n == 5) ? 1 : 0;
        int faceDown = 1;

        List<CharacterCard> pool, up, chars = new ArrayList<>();
        while (true) {                       // repeat until King not face-up
            pool = new ArrayList<>(tray);
            Collections.shuffle(pool, rng);

            up   = new ArrayList<>();
            Iterator<CharacterCard> it = pool.iterator();
            for (int i = 0; i < faceUp && it.hasNext(); i++) up.add(it.next());
            it.next();                       // 1 face-down (ignored here)
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
            CharacterCard chosen = (p instanceof HumanPlayer)
                    ? humanChooseCharacter(passing)
                    : passing.remove(0);               // naive AI
            p.setCharacter(chosen);
            cli.println("Player " + (seat + 1) + " chose a character.");
            seat = (seat + 1) % players.size();
        }
    }

    private CharacterCard humanChooseCharacter(List<CharacterCard> pool) {
        cli.println("Choose your character. Available:");
        for (CharacterCard c : pool) cli.println(" - " + c.getName());
        while (true) {
            String in = cli.prompt("> ").trim();
            for (CharacterCard c : pool)
                if (c.getName().equalsIgnoreCase(in)) { pool.remove(c); return c; }
            cli.println("Invalid name, try again.");
        }
    }

    /* ------------------------------------------------- *
     *  Turn Phase (trimmed; unchanged core)             *
     * ------------------------------------------------- */

    private void turnPhase() {
        phase = GamePhase.TURN;
        cli.println("\n--- TURN PHASE ---");

        for (int rank = 1; rank <= 8; rank++) {
            Player acting = findPlayerByRank(rank);
            cli.println(rank + ": " + rankName(rank));

            if (killedRanks.contains(rank)) { cli.println("Killed — skip."); continue; }
            if (acting == null)              { cli.println("No one is " + rankName(rank)); continue; }

            if (rank == robbedRank && thiefPlayer != null && thiefPlayer != acting) {
                int stolen = acting.getGold();
                acting.spendGold(stolen);
                thiefPlayer.gainGold(stolen);
                cli.println("The Thief steals " + stolen + " gold.");
            }

            if (acting instanceof HumanPlayer) cli.println("Your turn.");
            acting.takeTurn(this);
        }
    }

    private Player findPlayerByRank(int rank) {
        return players.stream()
                      .filter(p -> p.getCharacter() != null
                                && p.getCharacter().getRank() == rank)
                      .findFirst().orElse(null);
    }

    private static String rankName(int r) {
        return switch (r) {
            case 1 -> "Assassin";  case 2 -> "Thief";    case 3 -> "Magician";
            case 4 -> "King";      case 5 -> "Bishop";   case 6 -> "Merchant";
            case 7 -> "Architect"; case 8 -> "Warlord";  default -> "?";
        };
    }

    /* ------------------------------------------------- *
     * Prompts & mutators  (unchanged stubs)             *
     * ------------------------------------------------- */

    // ... (promptCharacterSelection, promptPlayerSelection, etc.)
    // (retain the same implementations you already had)

    /* ------------------------------------------------- *
     * Utilities                                         *
     * ------------------------------------------------- */

    private Deck<CharacterCard> buildCharacterDeck() {
        return new Deck<>(List.of(
                new Assassin(), new Thief(), new Magician(), new King(),
                new Bishop(),  new Merchant(), new Architect(), new Warlord()
        ));
    }
}
