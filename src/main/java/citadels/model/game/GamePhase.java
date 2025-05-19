// src/main/java/citadels/model/game/GamePhase.java
package citadels.model.game;

/**
 * The two repeating phases of every Citadels round.
 */
public enum GamePhase {
    /**
     * Players are passing around the character deck and secretly
     * choosing a character for the round.
     */
    SELECTION,

    /**
     * Characters act in rank order (1–8) and players take their turns.
     */
    TURN
}
