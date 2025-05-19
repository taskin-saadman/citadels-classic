package citadels.model.game;

import citadels.cli.CommandHandler;
import citadels.cli.ConsoleHandler;
import citadels.model.card.*;
import citadels.model.character.*;
import citadels.model.player.*;
import citadels.util.TSVLoader;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core game controller for Citadels Classic.
 */
public final class CitadelsGame {

    /* ------------------------------------------------- *
     *  Immutable config                                 *
     * ------------------------------------------------- */
    private final List<Player> players;
    private final CommandHandler cli;
    private final Random rng = new Random();

    /* ------------------------------------------------- *
     *  Runtime state                                    *
     * ------------------------------------------------- */
    private Deck<DistrictCard> districtDeck;
    private final Deck<CharacterCard> characterDeck = buildCharacterDeck();

    private int  crownedSeat = 0;
    private int  roundNo     = 1;
    private GamePhase phase  = GamePhase.SELECTION;

    /* per-round flags */
    private final Set<Integer> killedRanks = new HashSet<>();
    private int    robbedRank  = -1;
    private Player thiefPlayer = null;
    private final Set<Player> bishopProtected = new HashSet<>();
    private final Map<Player,Integer> builtThisTurn = new HashMap<>();

    /* ------------------------------------------------- *
     *  Construction                                     *
     * ------------------------------------------------- */
    public CitadelsGame(int nPlayers, CommandHandler cli) {
        if (nPlayers < 4 || nPlayers > 7)
            throw new IllegalArgumentException("Players must be 4-7");

        this.cli = cli;

        /* players */
        players = new ArrayList<>();
        players.add(new HumanPlayer(0));
        for (int i = 1; i < nPlayers; i++) players.add(new AIPlayer(i));

        /* deck */
        districtDeck = new Deck<>(TSVLoader.loadDistrictDeck());
        districtDeck.shuffle(rng);

        /* initial deal */
        for (Player p : players)
            for (int i = 0; i < 4; i++) p.addCardToHand(districtDeck.draw());
        // each player starts with 2 gold from Player ctor
    }

    /* ================================================= *
     *  Public engine control                            *
     * ================================================= */
    public void playRound() {
        cli.println("\n=== ROUND " + roundNo +
                " (Crown: Player " + (crownedSeat + 1) + ") ===");

        selectionPhase();
        turnPhase();

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

        Map<Player,Integer> score =
                ScoreCalculator.compute(players, firstDone);

        cli.println("\n=== FINAL SCORES ===");
        score.forEach((p,s) ->
                cli.println("Player " + (p.getId()+1) + ": " + s + " pts"));

        List<Player> winners = ScoreCalculator.winners(score);
        if (winners.size() == 1)
            cli.println("🥇 Congratulations Player " +
                    (winners.get(0).getId()+1) + "!");
        else
            cli.println("Tie! Highest-rank character breaks the tie.");
    }

    /* accessors used elsewhere */
    public List<Player> getPlayers() { return Collections.unmodifiableList(players); }
    int  getRound()               { return roundNo; }
    void setRound(int r)          { roundNo = r; }
    int  getCrownedSeat()         { return crownedSeat; }
    void setCrownedSeat(int s)    { crownedSeat = s; }
    List<String> getDistrictDeckNames() {
        return districtDeck.asListView().stream().map(Card::getName).collect(Collectors.toList());
    }
    void resetDistrictDeck(List<DistrictCard> ordered) {
        districtDeck = new Deck<>(ordered);
    }
    public CommandHandler cli() { return cli; }

    /* ------------------------------------------------- *
     *  Selection phase                                  *
     * ------------------------------------------------- */
    private void selectionPhase() {
        phase = GamePhase.SELECTION;
        cli.println("\n--- SELECTION PHASE ---");

        List<CharacterCard> tray = new ArrayList<>(characterDeck.asListView());
        Collections.shuffle(tray, rng);

        int n = players.size();
        int faceUp = (n == 4) ? 2 : (n == 5) ? 1 : 0;

        List<CharacterCard> pool, up, chars = new ArrayList<>();
        while (true) {
            pool = new ArrayList<>(tray);
            Collections.shuffle(pool, rng);

            up = new ArrayList<>();
            Iterator<CharacterCard> it = pool.iterator();
            for (int i = 0; i < faceUp && it.hasNext(); i++) up.add(it.next());
            it.next();                         // 1 face-down discard
            while (it.hasNext()) chars.add(it.next());

            if (up.stream().anyMatch(King.class::isInstance)) chars.clear();
            else break;
        }

        for (CharacterCard c : up) {
            cli.println(c.getName() + " was removed.");
            waitForHumanT();
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
            cli.println("Player " + (seat + 1) + " chose a character.");
            waitForHumanT();

            seat = (seat + 1) % players.size();
        }
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

    /* ------------------------------------------------- *
     *  Turn phase                                       *
     * ------------------------------------------------- */
    private void turnPhase() {
        phase = GamePhase.TURN;
        cli.println("\n--- TURN PHASE ---");

        for (int rank = 1; rank <= 8; rank++) {
            Player acting = findPlayerByRank(rank);

            cli.println(rank + ": " + rankName(rank));
            waitForHumanT();

            if (killedRanks.contains(rank)) {
                cli.println("Character was killed — skipping turn.");
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

            // King crown persistence
            if (rank == 4) crownedSeat = acting.getId();
            builtThisTurn.put(acting, 0);

            // robbery resolution
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

    /* ------------------------------------------------- *
     *  Pause helper                                     *
     * ------------------------------------------------- */
    private void waitForHumanT() {
        if (!(cli instanceof ConsoleHandler)) return;
        while (true) {
            String in = cli.prompt("> ").trim();
            if (in.equalsIgnoreCase("t")) return;
            cli.println("It is not your turn. Press t to continue with other player turns.");
        }
    }

    /* ------------------------------------------------- *
     *  Helpers & mutators (existing)                    *
     * ------------------------------------------------- */
    private Player findPlayerByRank(int rank) {
        return players.stream()
                .filter(p -> p.getCharacter() != null &&
                             p.getCharacter().getRank() == rank)
                .findFirst().orElse(null);
    }

    public static String rankName(int r) {                // now public
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

    /* getters/setters for save/load flags (omitted for brevity) …   */
    /* drawTwoChoose, buildDistrict, swapHands, etc. (existing code) */

    /* ------------------------------------------------- *
     *  Character deck builder                           *
     * ------------------------------------------------- */
    private Deck<CharacterCard> buildCharacterDeck() {
        return new Deck<>(Arrays.asList(
                new Assassin(), new Thief(), new Magician(), new King(),
                new Bishop(),  new Merchant(), new Architect(), new Warlord()
        ));
    }
}
