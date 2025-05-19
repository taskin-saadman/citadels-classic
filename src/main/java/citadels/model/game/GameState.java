package citadels.model.game;

import citadels.cli.CommandHandler;
import citadels.model.card.*;
import citadels.model.player.Player;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import citadels.util.CardRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Stateless helper able to (de)serialise a {@link CitadelsGame}.
 *
 * <p>This initial version stores enough information for assignment
 * requirements; feel free to extend as you add new features.</p>
 */
public final class GameState {

    private GameState() { /* utility */ }

    /* ------------------------------------------------- *
     * Serialise to JSON                                 *
     * ------------------------------------------------- */

    @SuppressWarnings("unchecked")
    public static JSONObject serialise(CitadelsGame game) {

        JSONObject root = new JSONObject();
        root.put("round", game.getRound());
        root.put("crownSeat", game.getCrownedSeat());

        /* --- players --- */
        JSONArray playersJson = new JSONArray();
        for (Player p : game.getPlayers()) {
            JSONObject po = new JSONObject();
            po.put("id", p.getId());
            po.put("gold", p.getGold());
            po.put("character", p.getCharacter() == null
                                ? 0 : p.getCharacter().getRank());

            po.put("hand", namesArray(p.getHand().stream().map(Card::getName)
                                                   .collect(Collectors.toList())));
            po.put("city", namesArray(p.getCity().stream().map(Card::getName)
                                                   .collect(Collectors.toList())));
            playersJson.add(po);
        }
        root.put("players", playersJson);

        /* --- district deck (names only) --- */
        root.put("districtDeck", namesArray(game.getDistrictDeckNames()));

        return root;
    }

    /* ------------------------------------------------- *
     * Deserialise from JSON                             *
     * ------------------------------------------------- */

    public static CitadelsGame deserialise(JSONObject root,
                                           CommandHandler cli,
                                           CardRepository repo) {

        int   playerCount = ((JSONArray) root.get("players")).size();
        int   round       = ((Number)   root.get("round")).intValue();
        int   crownSeat   = ((Number)   root.get("crownSeat")).intValue();

        CitadelsGame g = new CitadelsGame(playerCount, cli);
        g.setRound(round);
        g.setCrownedSeat(crownSeat);

        /* ---- restore player-specific state ---- */
        JSONArray playersJson = (JSONArray) root.get("players");
        for (Object o : playersJson) {
            JSONObject pj = (JSONObject) o;
            Player p = g.getPlayers().get(((Number) pj.get("id")).intValue());

            /* gold */
            g.gainGold(p, ((Number) pj.get("gold")).intValue() - p.getGold());

            /* character */
            int rank = ((Number) pj.get("character")).intValue();
            if (rank != 0) p.setCharacter(repo.characterByRank(rank));

            /* hand */
            for (Object n : (JSONArray) pj.get("hand")) {
                p.addCardToHand(repo.districtByName((String) n));
            }

            /* city */
            for (Object n : (JSONArray) pj.get("city")) {
                p.addDistrictToCity(repo.districtByName((String) n));
            }
        }

        /* ---- restore deck ---- */
        List<String> deckNames = (List<String>) (JSONArray) root.get("districtDeck");
        g.resetDistrictDeck(deckNames.stream()
                                     .map(repo::districtByName)
                                     .collect(Collectors.toList()));
        return g;
    }

    /* ------------------------------------------------- *
     * Helpers                                           *
     * ------------------------------------------------- */

    @SuppressWarnings("unchecked")
    private static JSONArray namesArray(List<String> names) {
        JSONArray a = new JSONArray();
        a.addAll(names);
        return a;
    }
}
