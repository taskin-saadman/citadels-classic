// src/main/java/citadels/model/player/Player.java
package citadels.model.player;

import citadels.model.card.CharacterCard;
import citadels.model.card.DistrictCard;

import java.util.*;

/**
 * Common state and helpers for both human and AI players.
 *
 * <p>Concrete subclasses implement {@link #takeTurn} and drive the
 * in-round decision making.</p>
 */
public abstract class Player {

    /* ------------------------------------------------- *
     * Immutable identity                                *
     * ------------------------------------------------- */

    private final int id;                     // 0-based seat order

    /* ------------------------------------------------- *
     * Mutable round-to-round state                      *
     * ------------------------------------------------- */

    protected final List<DistrictCard> hand  = new ArrayList<>();
    protected final List<DistrictCard> city  = new ArrayList<>();
    protected int gold                       = 2;
    protected CharacterCard character        = null;

    /** Default limit = 1; Architect may raise to 3 for the current turn. */
    protected int buildLimitThisTurn         = 1;

    /* ------------------------------------------------- *
     * Construction                                      *
     * ------------------------------------------------- */

    protected Player(int id) {
        this.id = id;
    }

    /* ------------------------------------------------- *
     * Abstract                                           *
     * ------------------------------------------------- */

    /** Called by the game engine when it’s this player’s moment in the Turn phase. */
    public abstract void takeTurn(citadels.model.game.CitadelsGame game);

    /* ------------------------------------------------- *
     * Convenience getters                               *
     * ------------------------------------------------- */

    public int                getId()          { return id; }
    public List<DistrictCard> getHand()        { return hand; }
    public List<DistrictCard> getCity()        { return city; }
    public int                getGold()        { return gold; }
    public CharacterCard      getCharacter()   { return character; }

    /* ------------------------------------------------- *
     * Mutators used by the engine / abilities           *
     * ------------------------------------------------- */

    public void setCharacter(CharacterCard c) {
        this.character = c;
        this.buildLimitThisTurn = 1;           // reset for new round
    }

    public void gainGold(int amount) {
        gold += amount;
    }

    public boolean spendGold(int amount) {
        if (amount > gold) return false;
        gold -= amount;
        return true;
    }

    public void addCardToHand(DistrictCard c) {
        hand.add(c);
    }

    public boolean cityContains(String cardName) {
        return city.stream().anyMatch(d -> d.getName().equals(cardName));
    }

    public void addDistrictToCity(DistrictCard c) {
        city.add(c);
    }

    public int getBuildLimit() {
        return buildLimitThisTurn;
    }

    public void setBuildLimit(int limit) {
        this.buildLimitThisTurn = limit;
    }

    @Override
    public String toString() {
        return "Player" + (id + 1) + " [gold=" + gold
                + ", hand=" + hand.size()
                + ", city=" + city.size() + ']';
    }
}
