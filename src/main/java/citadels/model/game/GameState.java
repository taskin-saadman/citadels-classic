// src/main/java/citadels/model/game/GameState.java
package citadels.model.game;

import citadels.cli.CommandHandler;
import citadels.model.card.*;
import citadels.model.player.Player;
import citadels.util.CardRepository;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.*;

/**
 * Serialises / deserialises a running game, now including mid-round flags:
 * killedRanks, robbedRank, and bishopProtected.
 */
public final class GameState {

    private GameState() {}

    /* ============================================================= *
     *  SERIALISE                                                    *
     * ============================================================= */
    @SuppressWarnings("unchecked")
    public static JSONObject serialise(CitadelsGame g) {

        JSONObject root = new JSONObject();
        root.put("round",        g.getRound());
        root.put("crownSeat",    g.getCrownedSeat());
        root.put("robbedRank",   g.getRobbedRank());

        /* killed rank set */
        JSONArray killed = new JSONArray();
        killed.addAll(g.getKilledRanks());
        root.put("killedRanks",  killed);

        /* bishop-protected seats */
        JSONArray prot = new JSONArray();
        for (Player p : g.getBishopProtected()) prot.add(p.getId());
        root.put("bishopProtectedSeats", prot);

        /* players */
        JSONArray players = new JSONArray();
        for (Player p : g.getPlayers()) players.add(serializePlayer(p));
        root.put("players", players);

        /* deck order */
        JSONArray deck = new JSONArray();
        deck.addAll(g.getDistrictDeckNames());
        root.put("districtDeck", deck);

        return root;
    }

    @SuppressWarnings("unchecked")
    private static JSONObject serializePlayer(Player p) {
        JSONObject jo = new JSONObject();
        jo.put("id",        p.getId());
        jo.put("gold",      p.getGold());
        jo.put("character", p.getCharacter() == null ? 0
                                                     : p.getCharacter().getRank());

        JSONArray hand = new JSONArray();
        for (DistrictCard d : p.getHand()) hand.add(d.getName());
        jo.put("hand", hand);

        JSONArray city = new JSONArray();
        for (DistrictCard d : p.getCity()) city.add(d.getName());
        jo.put("city", city);
        return jo;
    }

    /* ============================================================= *
     *  DESERIALISE                                                  *
     * ============================================================= */
    public static CitadelsGame deserialise(JSONObject root,
                                           CommandHandler io,
                                           CardRepository repo) {

        int playerCount = ((JSONArray) root.get("players")).size();
        CitadelsGame g  = new CitadelsGame(playerCount, io);

        /* basic round / crown */
        g.setRound( ((Number) root.get("round")).intValue() );
        g.setCrownedSeat( ((Number) root.get("crownSeat")).intValue() );

        /* robbery / kill flags */
        g.setRobbedRank( ((Number) root.get("robbedRank")).intValue() );

        Set<Integer> killed = new HashSet<>();
        for (Object o : (JSONArray) root.get("killedRanks"))
            killed.add(((Number) o).intValue());
        g.setKilledRanks(killed);

        /* bishop protected */
        Set<Player> prot = new HashSet<>();
        for (Object o : (JSONArray) root.get("bishopProtectedSeats")) {
            int seat = ((Number) o).intValue();
            prot.add(g.getPlayer(seat));
        }
        g.setBishopProtected(prot);

        /* players */
        JSONArray players = (JSONArray) root.get("players");
        for (Object o : players) restorePlayer(g, (JSONObject) o, repo);

        /* deck */
        List<String> deckNames = new ArrayList<>();
        for (Object o : (JSONArray) root.get("districtDeck"))
            deckNames.add((String) o);
        List<DistrictCard> ordered = new ArrayList<>();
        for (String n : deckNames) ordered.add(repo.districtByName(n));
        g.resetDistrictDeck(ordered);

        return g;
    }

    private static void restorePlayer(CitadelsGame g,
                                      JSONObject jo,
                                      CardRepository repo) {

        Player p = g.getPlayer(((Number) jo.get("id")).intValue());

        /* character */
        int rank = ((Number) jo.get("character")).intValue();
        if (rank != 0) p.setCharacter(repo.characterByRank(rank));

        /* gold */
        int gold = ((Number) jo.get("gold")).intValue();
        p.gainGold(gold - p.getGold());

        /* hand */
        for (Object o : (JSONArray) jo.get("hand"))
            p.addCardToHand(repo.districtByName((String) o));

        /* city */
        for (Object o : (JSONArray) jo.get("city"))
            p.addDistrictToCity(repo.districtByName((String) o));
    }
}
