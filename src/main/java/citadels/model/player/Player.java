package citadels.model.player;

import citadels.model.card.CharacterCard;
import citadels.model.card.DistrictCard;
import java.util.*;

/**
 * Abstract parent class for both human and AI players.
 *
 * <p>Concrete subclasses implement {@link #takeTurn} and drive the
 * in-round decision making.</p>
 */
public abstract class Player {

    /* ------------------------------------------------- *
     * Immutable identity                                *
     * ------------------------------------------------- */

    private final int id;            // 0-based seat order

    /* ------------------------------------------------- *
     * Mutable round-to-round state                      *
     * ------------------------------------------------- */

    protected final List<DistrictCard> hand = new ArrayList<>(); //hand of cards
    protected final List<DistrictCard> city = new ArrayList<>(); //city of cards
    protected int gold = 2; //gold (starting gold)
    protected CharacterCard character = null; //character

    // Default limit = 1; Architect may raise to 3 for the current turn.
    protected int buildLimitThisTurn = 1; //build limit

    /* ------------------------------------------------- *
     * Construction                                      *
     * ------------------------------------------------- */

    protected Player(int id) { //construct using id (immutable 0-based seat order)
        this.id = id;
    }

    /* ------------------------------------------------- *
     * Abstract                                           *
     * ------------------------------------------------- */

    /** Called by the game engine when it’s this player’s moment in the Turn phase. */
    public abstract void takeTurn(citadels.model.game.CitadelsGame game); //must be implemented by subclasses

    /* ------------------------------------------------- *
     * Convenience getters                               *
     * ------------------------------------------------- */

    public int getId() { return id; } //get id of the player
    public List<DistrictCard> getHand() { return hand; } //get hand
    public List<DistrictCard> getCity() { return city; } //get city
    public int getGold() { return gold; } //get gold
    public CharacterCard getCharacter() { return character; } //get character

    /* ------------------------------------------------- *
     * Mutators used by the engine / abilities           *
     * ------------------------------------------------- */

    /** Set the character of the player */
    public void setCharacter(CharacterCard c) {
        this.character = c;
        this.buildLimitThisTurn = 1;    // reset for new round
    }

    public void gainGold(int amount) {
        gold += amount;
    }

    /** Spend gold */
    public boolean spendGold(int amount) {
        if (amount > gold) return false;
        gold -= amount;
        return true;
    }

    /** Add a card to the hand */
    public void addCardToHand(DistrictCard c) {
        hand.add(c);
    }

    /** Check if the city contains a card with the given name */
    public boolean cityContains(String cardName) { //helpful for confirming that 2 same districts are not in the city
        return city.stream().anyMatch(d -> d.getName().equals(cardName));
    }

    /** Add a district to the city */
    public void addDistrictToCity(DistrictCard c) {
        city.add(c);
    }

    /** Get the build limit for player in current turn (for architect it's 3)*/
    public int getBuildLimit() {
        return buildLimitThisTurn;
    }

    /** Set the build limit for player in current turn (for architect it's 3)*/
    public void setBuildLimit(int limit) {
        this.buildLimitThisTurn = limit;
    }

    /** String representation of the player */
    @Override
    public String toString() {
        return "Player" + (id + 1) + " [gold=" + gold
                + ", hand=" + hand.size()
                + ", city=" + city.size() + ']';
    }
}
