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
    public CitadelsGame getGame()          { return game; }

    /* ---- debug ---- */
    public void toggleDebug()  { debug = !debug; }
    public boolean isDebug()   { return debug;   }

    /* ---- repository access for load() ---- */
    public CardRepoSingleton cardRepo() { return CardRepoSingleton.INSTANCE; }

    /* ---- io ---- */
    public void println(String m){ System.out.println(m); }
    public String prompt(String m){ System.out.print(m); return in.nextLine(); }

    /* ask player count unchanged */
}
