package citadels;

import citadels.cli.ConsoleHandler;
import citadels.model.game.CitadelsGame;

/**
 * Entry-point for the Citadels Classic CLI application.
 *
 * <p>Runs exactly in the order shown in the assignment PDF:</p>
 * <pre>
 * Enter how many players [4-7]:
 * &gt; 6
 * Shuffling deck...
 * Adding characters...
 * Dealing cards...
 * Starting Citadels with 6 players...
 * You are player 1
 * </pre>
 */
public final class App {

    public static void main(String[] args) {

        /* Console I/O handler (System.in/out) */
        ConsoleHandler io = new ConsoleHandler();

        /* ---- initial prompt ---- */
        int nPlayers = io.askPlayers();          // loops until 4-7 entered

        /* ---- assignment-spec banner ---- */
        io.println("Shuffling deck...");
        io.println("Adding characters...");
        io.println("Dealing cards...");

        /* Build game AFTER banner (deck is shuffled in ctor) */
        CitadelsGame game = new CitadelsGame(nPlayers, io);
        io.attachGame(game);                     // back-link for CLI

        io.println("Starting Citadels with " + nPlayers + " players...");
        io.println("You are player 1");

        /* ---- main game loop ---- */
        while (!game.isGameOver()) {
            game.playRound();
        }

        /* ---- final scores ---- */
        game.scoreAndPrintResults();
    }

    /* prevent instantiation */
    private App() { }
}