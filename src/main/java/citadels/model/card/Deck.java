// src/main/java/citadels/model/card/Deck.java
package citadels.model.card;

import java.util.*;

/**
 * A small, lightweight deck implementation for Citadels.
 *
 * @param <T> any concrete subtype of {@link Card}
 *
 * <p>Internally backed by an {@link ArrayDeque} so that<br>
 * <code>draw()</code> = pop from top, <code>putOnBottom()</code> = push to bottom.</p>
 */
public final class Deck<T extends Card> {

    /** Index 0 = top of deck, tail = bottom. */
    private final Deque<T> cards = new ArrayDeque<>();

    /* ------------------------------------------------- *
     * Construction & Population                         *
     * ------------------------------------------------- */

    /** Empty deck – populate later with {@link #add} or {@link #addAll}. */
    public Deck() {}

    /** Convenience ctor to build from an existing collection (keeps order). */
    public Deck(Collection<? extends T> initial) {
        cards.addAll(initial);
    }

    /** Add a single card on top (useful when building the starting deck). */
    public void add(T card) {
        cards.addFirst(card);
    }

    /** Add a batch, preserving the iteration order (first element ends up on top). */
    public void addAll(Collection<? extends T> batch) {
        // iterate in reverse to keep intuitive “first becomes top” order
        List<? extends T> list = new ArrayList<>(batch);
        ListIterator<? extends T> it = list.listIterator(list.size());
        while (it.hasPrevious()) cards.addFirst(it.previous());
    }

    /* ------------------------------------------------- *
     * Core Operations                                   *
     * ------------------------------------------------- */

    /** Randomise remaining order. */
    public void shuffle(Random rng) {
        List<T> list = new ArrayList<>(cards);
        Collections.shuffle(list, rng);
        cards.clear();
        cards.addAll(list);
    }

    /** Draw from the top; throws {@link NoSuchElementException} if empty. */
    public T draw() {
        return cards.removeFirst();
    }

    /** Non-destructive look at the next card to be drawn, or {@code null} if empty. */
    public T peek() {
        return cards.peekFirst();
    }

    /** Place card on the bottom. */
    public void putOnBottom(T card) {
        cards.addLast(card);
    }

    /* ------------------------------------------------- *
     * Informational                                     *
     * ------------------------------------------------- */

    public int size()            { return cards.size(); }
    public boolean isEmpty()     { return cards.isEmpty(); }
    public List<T> asListView()  { return Collections.unmodifiableList(new ArrayList<>(cards)); }

    @Override
    public String toString() {
        return "Deck(" + size() + " cards)";
    }
}
