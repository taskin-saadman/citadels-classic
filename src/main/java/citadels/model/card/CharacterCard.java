// src/main/java/citadels/model/card/CharacterCard.java
package citadels.model.card;

import citadels.model.character.Ability;

/**
 * Abstract base for all eight character cards.
 * Each concrete subclass (Assassin, Thief, …) defines {@link #use}.
 */
public abstract class CharacterCard extends Card implements Ability {

    private final int rank;   // 1–8 inclusive

    protected CharacterCard(String name, int rank) {
        super(name);
        if (rank < 1 || rank > 8)
            throw new IllegalArgumentException("Rank must be 1–8: " + rank);
        this.rank = rank;
    }

    /** Rank order used during the Turn phase (1 = Assassin, … 8 = Warlord). */
    public int getRank() {
        return rank;
    }
}
