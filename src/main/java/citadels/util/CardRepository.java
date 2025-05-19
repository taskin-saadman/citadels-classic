// src/main/java/citadels/util/CardRepository.java
package citadels.util;

import citadels.model.card.DistrictCard;
import citadels.model.card.CharacterCard;

/**
 * Read-only lookup table for cards by name or rank.
 * Implemented by {@link CardRepoSingleton}.
 */
public interface CardRepository {

    DistrictCard  districtByName(String name);

    /** Returns a prototype CharacterCard for the given rank (1–8). */
    CharacterCard characterByRank(int rank);
}
