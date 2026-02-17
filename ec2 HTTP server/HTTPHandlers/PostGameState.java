package HTTPHandlers;

import GameHandlers.Bot;
import GameHandlers.Card;
import GameHandlers.Game;
import GameHandlers.GamePhase;
import GameHandlers.Player;
import GameHandlers.Suits;
import GameHandlers.User;
import org.json.JSONArray;
import org.json.JSONObject;

public class PostGameState {
    public static void postGameState(Game game) {
        if (game == null) {
            return;
        }
        for (Player player : game.getPlayers()) {
            if (!(player instanceof User)) {
                continue;
            }
            User user = (User) player;
            if (!user.isOnline()) {
                continue;
            }
            try {
                JSONObject payload = buildPayload(game, user);
                AWSSigner.sendSignedMessage(payload.toString(), user.getConnections());
            } catch (Exception e) {
                System.out.println("Failed to post game state: " + e);
            }
        }
    }

    private static JSONObject buildPayload(Game game, User user) {
        JSONObject payload = new JSONObject();
        payload.put("returnType", "gameState");
        payload.put("gameID", game.getGameID());
        payload.put("state", game.getState().toString());
        payload.put("phase", game.getPhase().toString());
        payload.put("round", game.getRound());
        payload.put("cardsDealt", game.getCardsDealt());
        payload.put("actionLocked", game.isActionLocked());
        payload.put("trickCounter", game.getTrickCounter());

        Card trump = game.getTrump();
        payload.put("trump", trump != null ? trump.getKey() : "");

        Suits leadSuit = game.getLeadSuit();
        payload.put("leadSuit", leadSuit != null ? leadSuit.getDisplayName() : "");

        JSONArray table = new JSONArray();
        for (Card card : game.getTableCards()) {
            JSONObject entry = new JSONObject();
            entry.put("card", card.getKey());
            entry.put("seat", card.getPlayedByIndex());
            table.put(entry);
        }
        payload.put("table", table);

        JSONObject turn = new JSONObject();
        int turnIndex = game.getCurrentTurnIndex();
        int bettingLeadSeat = game.getInitiatorIndex();
        payload.put("bettingLeadSeat", bettingLeadSeat);
        payload.put("bidLeadSeat", bettingLeadSeat);
        if (turnIndex >= 0 && turnIndex < game.getPlayers().size()) {
            Player current = game.getPlayers().get(turnIndex);
            turn.put("seat", current.getSeatIndex());
            turn.put("name", current.getUsername());
            turn.put("type", game.getPhase() == GamePhase.BETTING ? "BET" : "PLAY");
        }
        payload.put("turn", turn);
        int lastWinner = game.getLastTrickWinnerIndex();
        payload.put("lastTrickWinner", lastWinner >= 0 ? lastWinner : JSONObject.NULL);

        JSONArray players = new JSONArray();
        for (Player player : game.getPlayers()) {
            JSONObject playerJson = new JSONObject();
            playerJson.put("seat", player.getSeatIndex());
            playerJson.put("name", player.getUsername());
            playerJson.put("isBot", player instanceof Bot);
            playerJson.put("isOffline", player instanceof User && !((User) player).isOnline());
            playerJson.put("bet", player.hasPlacedBet() ? player.getBet() : JSONObject.NULL);
            playerJson.put("handsWon", player.getHandsWon());
            playerJson.put("score", player.getScore());
            playerJson.put("cardsInHand", player.getHand().size());
            players.put(playerJson);
        }
        payload.put("players", players);

        payload.put("yourSeat", user.getSeatIndex());
        payload.put("yourBet", user.hasPlacedBet() ? user.getBet() : JSONObject.NULL);
        JSONArray hand = new JSONArray();
        for (Card card : user.getHand()) {
            hand.put(card.getKey());
        }
        payload.put("hand", hand);

        return payload;
    }
}
