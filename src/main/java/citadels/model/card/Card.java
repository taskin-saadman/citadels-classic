// src/main/java/citadels/model/card/Card.java
package citadels.model.card;

import java.util.Objects;

/**
 * Common superclass for every card type in Citadels.
 */
public abstract class Card {

    private final String name;

    protected Card(String name) {
        this.name = Objects.requireNonNull(name, "Card name cannot be null");
    }

    /** Card title exactly as printed on the physical card. */
    public String getName() {
        return name;
    }

    /* ---------- equality / hashing on name only ---------- */

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Card)) return false;
        return name.equals(((Card) o).name);
    }

    @Override
    public final int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
