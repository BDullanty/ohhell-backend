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
    private static final String SCOPE = "PostGameState";

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
                ServerLog.error(
                    SCOPE,
                    "Failed posting game state for user " + user.getUsername() + " in game " + game.getGameID(),
                    e
                );
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
        payload.put("turnTimeLimitSeconds", game.getTurnTimeLimitSeconds());
        long turnDeadlineMs = game.getTurnDeadlineMs();
        payload.put("turnDeadlineMs", turnDeadlineMs > 0 ? turnDeadlineMs : JSONObject.NULL);

        Card trump = game.getTrump();
        payload.put("trump", trump != null ? trump.getKey() : "");

        Suits leadSuit = game.getLeadSuit();
        payload.put("leadSuit", leadSuit != null ? leadSuit.getDisplayName() : "");
        payload.put("table", buildTableJson(game));
        JSONObject turn = buildTurnJson(game);
        payload.put("turn", turn != null ? turn : JSONObject.NULL);
        payload.put("bettingLeadSeat", game.getInitiatorIndex());

        int lastWinner = game.getLastTrickWinnerIndex();
        payload.put("lastTrickWinner", lastWinner >= 0 ? lastWinner : JSONObject.NULL);
        payload.put("players", buildPlayersJson(game));

        payload.put("yourSeat", user.getSeatIndex());
        payload.put("yourBet", user.hasPlacedBet() ? user.getBet() : JSONObject.NULL);
        payload.put("hand", buildHandJson(user));

        return payload;
    }

    private static JSONArray buildTableJson(Game game) {
        JSONArray table = new JSONArray();
        for (Card card : game.getTableCards()) {
            JSONObject entry = new JSONObject();
            entry.put("card", card.getKey());
            entry.put("seat", card.getPlayedByIndex());
            table.put(entry);
        }
        return table;
    }

    private static JSONObject buildTurnJson(Game game) {
        int turnIndex = game.getCurrentTurnIndex();
        if (turnIndex < 0 || turnIndex >= game.getPlayers().size()) {
            return null;
        }
        Player current = game.getPlayers().get(turnIndex);
        JSONObject turn = new JSONObject();
        turn.put("seat", current.getSeatIndex());
        turn.put("name", current.getUsername());
        turn.put("type", game.getPhase() == GamePhase.BETTING ? "BET" : "PLAY");
        return turn;
    }

    private static JSONArray buildPlayersJson(Game game) {
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
        return players;
    }

    private static JSONArray buildHandJson(User user) {
        JSONArray hand = new JSONArray();
        for (Card card : user.getHand()) {
            hand.put(card.getKey());
        }
        return hand;
    }
}
