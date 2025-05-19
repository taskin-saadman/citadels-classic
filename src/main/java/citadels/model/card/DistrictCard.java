// src/main/java/citadels/model/card/DistrictCard.java
package citadels.model.card;

/**
 * A buildable district card (yellow, blue, green, red, or purple).
 */
public final class DistrictCard extends Card {

    private final DistrictColor color;
    private final int cost;
    private final String specialText;  // null/empty if no special rule

    public DistrictCard(String name,
                        DistrictColor color,
                        int cost,
                        String specialText) {
        super(name);
        this.color = color;
        this.cost  = cost;
        this.specialText = (specialText == null || specialText.isEmpty())
                           ? null : specialText;
    }

    public DistrictColor getColor() {
        return color;
    }

    public int getCost() {
        return cost;
    }

    /** @return rule text for purple districts, or {@code null}. */
    public String getSpecialText() {
        return specialText;
    }

    @Override
    public String toString() {
        return String.format("%s [%s%d]", getName().replace('_', ' '),
                             color.name().toLowerCase(), cost);
    }
}
