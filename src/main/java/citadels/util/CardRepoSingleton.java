// src/main/java/citadels/util/CardRepoSingleton.java
package citadels.util;

import citadels.model.card.*;
import citadels.model.character.*;

import java.util.*;

/**
 * Single, lazily initialised repository of every card in the classic set.
 */
public enum CardRepoSingleton implements CardRepository {
    INSTANCE;

    /* ------------------------------------------------------------------ */
    private final Map<String, DistrictCard> districtMap;
    private final Map<Integer, CharacterCard> characterMap;

    CardRepoSingleton() {
        /* load every district once from TSV */
        List<DistrictCard> all = TSVLoader.loadDistrictDeck();
        Map<String, DistrictCard> m = new HashMap<>();
        for (DistrictCard d : all) m.putIfAbsent(d.getName(), d); // keep one prototype
        districtMap = Collections.unmodifiableMap(m);

        /* build character prototypes (new each round if you prefer) */
        Map<Integer, CharacterCard> c = new HashMap<>();
        c.put(1, new Assassin());
        c.put(2, new Thief());
        c.put(3, new Magician());
        c.put(4, new King());
        c.put(5, new Bishop());
        c.put(6, new Merchant());
        c.put(7, new Architect());
        c.put(8, new Warlord());
        characterMap = Collections.unmodifiableMap(c);
    }

    /* ------------------------------------------------------------------ */
    @Override
    public DistrictCard districtByName(String name) {
        DistrictCard d = districtMap.get(name);
        if (d == null) throw new IllegalArgumentException("No district: " + name);
        return d;
    }

    @Override
    public CharacterCard characterByRank(int rank) {
        CharacterCard c = characterMap.get(rank);
        if (c == null) throw new IllegalArgumentException("No character rank: " + rank);
        /* return a NEW instance to avoid shared state */
        return characterPrototypeCopy(c);
    }

    private CharacterCard characterPrototypeCopy(CharacterCard proto) {
        switch (proto.getRank()) {
            case 1: return new Assassin();
            case 2: return new Thief();
            case 3: return new Magician();
            case 4: return new King();
            case 5: return new Bishop();
            case 6: return new Merchant();
            case 7: return new Architect();
            case 8: return new Warlord();
            default: throw new IllegalStateException();
        }
    }
}
