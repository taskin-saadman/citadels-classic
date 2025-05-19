// src/main/java/citadels/cli/ConsoleHandler.java
package citadels.cli;

import citadels.model.game.CitadelsGame;

import java.util.Scanner;

/**
 * System.in/out concrete implementation of {@link CommandHandler}.
 */
public final class ConsoleHandler implements CommandHandler {

    private final Scanner in = new Scanner(System.in);
    private CitadelsGame game;

    /* ------------------------------------------------- *
     * Wiring                                            *
     * ------------------------------------------------- */

    public void attachGame(CitadelsGame game) {
        this.game = game;
    }
    public CitadelsGame getGame() { return game; }

    /* ------------------------------------------------- *
     * Utility prompt helpers                            *
     * ------------------------------------------------- */

    @Override
    public void println(String msg) {
        System.out.println(msg);
    }

    @Override
    public String prompt(String msg) {
        System.out.print(msg);
        return in.nextLine();
    }

    /* ------------------------------------------------- *
     * Extra helper used by App BEFORE engine exists     *
     * ------------------------------------------------- */

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
}
