package citadels;

import citadels.cli.ConsoleHandler;
import citadels.cli.CommandHandler;
import citadels.model.game.CitadelsGame;
import citadels.model.player.Player;

/**
 * Entry-point for the Citadels Classic CLI application.
 */
public final class App {

    public static void main(String[] args) {

        /* Console I/O handler (System.in/out) */
        ConsoleHandler io = new ConsoleHandler();
        CitadelsGame game;

        // prompt to load a saved game
        io.println("Do you want to load a saved game? (y/n)");
        String load = io.askString();
        if (load.equalsIgnoreCase("y")) {
            io.println("Enter the file name (add .json to the end):");
            String fileName = io.askString();
            io.println("Loading game from " + fileName);
            CitadelsGame loaded = CommandHandler.loadGame(fileName, io);
            io.attachGame(loaded);
            game = loaded;
        } else { // if the user does not want to load a saved game
            /* ---- initial prompt ---- */
            int nPlayers = io.askPlayers();          // loops until 4-7 entered

            /* ---- assignment-spec banner ---- */
            io.println("Shuffling deck...");
            io.println("Adding characters...");
            io.println("Dealing cards...");

            /* Build game AFTER banner (deck is shuffled in ctor) */
            CitadelsGame newGame = new CitadelsGame(nPlayers, io);
            io.attachGame(newGame);                     // back-link for CLI

            io.println("Starting Citadels with " + nPlayers + " players...");
            io.println("You are player 1");

            game = newGame;
        }

        /* ---- main game loop ---- */
        CommandHandler handler = io;
        /* ---- main game loop ---- */
        while (!io.getGame().isGameOver()) {
            io.getGame().playRound();
        }

        /* ---- final scores ---- */
        game.scoreAndPrintResults();
    }
}
