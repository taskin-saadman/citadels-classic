package citadels.cli;

import citadels.model.game.CitadelsGame;
import citadels.util.CardRepoSingleton;   // your CardRepository impl

import java.util.Scanner;

public final class ConsoleHandler implements CommandHandler {

    private final Scanner in = new Scanner(System.in);
    private CitadelsGame  game;
    private boolean       debug = false;

    /* ---- wiring ---- */
    public void attachGame(CitadelsGame g) { this.game = g; }
    public CitadelsGame getGame() { return game; }

    /* ---- debug ---- */
    public void toggleDebug()  { 
        debug = !debug;
        if (!debug) println("Disabled debug mode. You will no longer see all player’s hands.");
     }

        /* ------------------------------------------------------------------ *
     *  Prompt for player count (4-7)                                     *
     * ------------------------------------------------------------------ */
    public int askPlayers() {
        while (true) {
            try {
                String raw = prompt("Enter how many players [4-7]:\n> ");
                int n = Integer.parseInt(raw.trim());
                if (n >= 4 && n <= 7) return n;
            } catch (NumberFormatException ignore) { }
            println("Please enter a number between 4 and 7.");
        }
    }


    public boolean isDebug()   { return debug;   }

    /* ---- repository access for load() ---- */
    public CardRepoSingleton cardRepo() { return CardRepoSingleton.INSTANCE; }

    /* ---- io ---- */
    public void println(String m){ System.out.println(m); }
    public String prompt(String m){ System.out.print(m); return in.nextLine(); }

    /* ask player count unchanged */
}
