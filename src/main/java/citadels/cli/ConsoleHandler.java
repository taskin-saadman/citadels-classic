package citadels.cli;

import citadels.model.game.CitadelsGame;
import citadels.util.CardRepoSingleton;
import java.util.Scanner;

/**
 * Immutable class that implements the CommandHandler interface.
 * Handles the console input and output.
 */
public final class ConsoleHandler implements CommandHandler {

    private final Scanner in = new Scanner(System.in);
    private CitadelsGame game;
    private boolean debug = false;

    /**
     * Attaches the game to the console handler.
     * @param g the game to attach
     */
    public void attachGame(CitadelsGame g) { this.game = g; }

    /**
     * Returns the game attached to the console handler.
     * @return the game attached to the console handler
     */
    public CitadelsGame getGame() { return game; }

    /**
     * Toggles the debug mode on and off.
     */
    public void toggleDebug()  { 
        debug = !debug;
        if (!debug) println("Disabled debug mode. You will no longer see all player's hands.");
        if (debug) println("Enabled debug mode. You will now see all player's hands.");
     }

     //make a method to prompt for loading a saved game at the very start of the game (later)

    /**
     * Prompts the user to enter the number of players (4-7) at the start of the game.
     * @return the number of players
     */
    public int askPlayers() {
        while (true) {
            String raw = prompt("Enter how many players [4-7]:\n> ");
            int n = Integer.parseInt(raw.trim());
            if (n >= 4 && n <= 7) return n;   
        }
    }

    /**
     * Returns the debug mode.
     * @return the debug mode
     */
    public boolean isDebug()   { return debug;   }


    /**
     * Returns the card repository singleton.
     * repository is used to load the cards from the JSON file.
     * @return the card repository singleton
     */
    public CardRepoSingleton cardRepo() { return CardRepoSingleton.INSTANCE; }

    /**
     * Prints a message to the console.
     * @param m the message to print
     */
    public void println(String m){ System.out.println(m); }

    /**
     * Prompts the user for input.
     * @param m the prompt message
     * @return the user's input
     */
    public String prompt(String m){ System.out.print(m); return in.nextLine(); }

}
