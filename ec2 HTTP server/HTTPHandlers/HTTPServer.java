package HTTPHandlers;

import GameHandlers.Game;
import GameHandlers.GameHandler;
import GameHandlers.State;
import GameHandlers.User;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static HTTPHandlers.ExchangeHandler.getInfoJsonFromExchange;

public class HTTPServer {
    private static final String SCOPE = "HTTPServer";

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/Connect", new ConnectHandler());
        server.createContext("/Disconnect", new DisconnectHandler());
        server.createContext("/ListPlayers", new ListPlayers());
        server.createContext("/CreateGame", new CreateGameHandler());
        server.createContext("/LeaveGame", new LeaveGameHandler());
        server.createContext("/JoinGame", new JoinGameHandler());
        server.createContext("/VoteStart", new StartGameHandler());
        server.createContext("/PlayCard", new PlayCardHandler());
        server.createContext("/Bet", new BetHandler());
        server.createContext("/ForfeitGame", new ForfeitGameHandler());
        server.createContext("/SetCardBack", new SetCardBackHandler());
        server.createContext("/SetCardFront", new SetCardFrontHandler());
        server.setExecutor(null);
        server.start();
        ServerLog.info(SCOPE, "HTTP server started on port 8080");
    }

    private static void send(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendJson(HttpExchange exchange, int statusCode, JSONObject response) throws IOException {
        send(exchange, statusCode, response.toString());
    }

    private static JSONObject errorJson(String message) {
        JSONObject json = new JSONObject();
        json.put("error", message);
        return json;
    }

    private static User requireUser(JSONObject infoJson) {
        String connectionId = infoJson.optString("connectionID", "");
        if (connectionId.isBlank()) {
            throw new IllegalArgumentException("Missing connectionID.");
        }
        User user = User.getUser(connectionId);
        if (user == null) {
            throw new IllegalArgumentException("Unknown connection: " + connectionId);
        }
        return user;
    }

    private static String getCardKey(JSONObject infoJson) {
        return infoJson.optString("card", "");
    }

    private static int getBet(JSONObject infoJson) {
        return infoJson.optInt("bet", -1);
    }

    private static String getCardBack(JSONObject infoJson) {
        String cardBack = infoJson.optString("cardBack", "");
        if (cardBack.isBlank()) {
            cardBack = infoJson.optString("cardBackKey", "");
        }
        if (cardBack.isBlank()) {
            cardBack = infoJson.optString("back", "");
        }
        if (cardBack.isBlank()) {
            cardBack = infoJson.optString("deckBack", "");
        }
        return cardBack;
    }

    private static String getCardFront(JSONObject infoJson) {
        String cardFront = infoJson.optString("cardFront", "");
        if (cardFront.isBlank()) {
            cardFront = infoJson.optString("cardFrontKey", "");
        }
        if (cardFront.isBlank()) {
            cardFront = infoJson.optString("front", "");
        }
        if (cardFront.isBlank()) {
            cardFront = infoJson.optString("deckFront", "");
        }
        return cardFront;
    }

    static class ConnectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = Connect.connectPlayer(exchange);
            ServerLog.info(SCOPE, "/Connect response sent: " + response);
        }
    }

    static class DisconnectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Disconnect.disconnectPlayer(exchange);
        }
    }

    static class PlayCardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JSONObject infoJson = getInfoJsonFromExchange(exchange);
                User user = requireUser(infoJson);
                String cardKey = getCardKey(infoJson);
                GameHandler.handlePlay(user, cardKey);
                JSONObject response = new JSONObject();
                response.put("returnType", "ack");
                response.put("action", "PlayCard");
                response.put("status", "received");
                sendJson(exchange, 200, response);
            } catch (Exception e) {
                ServerLog.error(SCOPE, "PlayCard request failed.", e);
                sendJson(exchange, 400, errorJson("Bad play request."));
            }
        }
    }

    static class BetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JSONObject infoJson = getInfoJsonFromExchange(exchange);
                User user = requireUser(infoJson);
                int bet = getBet(infoJson);
                GameHandler.handleBet(user, bet);
                JSONObject response = new JSONObject();
                response.put("returnType", "ack");
                response.put("action", "Bet");
                response.put("status", "received");
                sendJson(exchange, 200, response);
            } catch (Exception e) {
                ServerLog.error(SCOPE, "Bet request failed.", e);
                sendJson(exchange, 400, errorJson("Bad bet request."));
            }
        }
    }

    static class ForfeitGameHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JSONObject infoJson = getInfoJsonFromExchange(exchange);
                User user = requireUser(infoJson);
                GameHandler.forfeitUser(user);
                JSONObject response = new JSONObject();
                response.put("returnType", "ack");
                response.put("action", "ForfeitGame");
                response.put("status", "received");
                sendJson(exchange, 200, response);
            } catch (Exception e) {
                ServerLog.error(SCOPE, "ForfeitGame request failed.", e);
                sendJson(exchange, 400, errorJson("Bad forfeit request."));
            }
        }
    }

    static class SetCardBackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JSONObject infoJson = getInfoJsonFromExchange(exchange);
                User user = requireUser(infoJson);
                String requestedCardBack = getCardBack(infoJson);
                user.setCardBack(requestedCardBack);

                JSONObject response = new JSONObject();
                response.put("returnType", "ack");
                response.put("action", "SetCardBack");
                response.put("status", "received");
                sendJson(exchange, 200, response);

                PostUserInfo.postUserInfo(user);
                PostAllUsersToLobby.postAllUsersToLobby();
                Game game = GameHandler.getGame(user.getGameID());
                if (game != null) {
                    PostGameState.postGameState(game);
                }
            } catch (Exception e) {
                ServerLog.error(SCOPE, "SetCardBack request failed.", e);
                sendJson(exchange, 400, errorJson("Bad card back request."));
            }
        }
    }

    static class SetCardFrontHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JSONObject infoJson = getInfoJsonFromExchange(exchange);
                User user = requireUser(infoJson);
                String requestedCardFront = getCardFront(infoJson);
                user.setCardFront(requestedCardFront);

                JSONObject response = new JSONObject();
                response.put("returnType", "ack");
                response.put("action", "SetCardFront");
                response.put("status", "received");
                sendJson(exchange, 200, response);

                PostUserInfo.postUserInfo(user);
                PostAllUsersToLobby.postAllUsersToLobby();
                Game game = GameHandler.getGame(user.getGameID());
                if (game != null) {
                    PostGameState.postGameState(game);
                }
            } catch (Exception e) {
                ServerLog.error(SCOPE, "SetCardFront request failed.", e);
                sendJson(exchange, 400, errorJson("Bad card front request."));
            }
        }
    }

    static class CreateGameHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JSONObject infoJson = getInfoJsonFromExchange(exchange);
                User user = requireUser(infoJson);

                if (user.getGameID() != -1) {
                    Game oldGame = GameHandler.getGame(user.getGameID());
                    GameHandler.removeUserFromGame(user);
                    if (oldGame != null && oldGame.getPlayers().isEmpty()) {
                        GameHandler.end(oldGame);
                    }
                }

                Game game = new Game(user);
                GameHandler.addGameToLobby(game);
                ServerLog.info(
                    SCOPE,
                    String.format("Created game %d for host %s", game.getGameID(), user.getUsername())
                );

                JSONObject response = new JSONObject();
                response.put("gameID", game.getGameID());
                sendJson(exchange, 200, response);

                PostUserInfo.postUserInfo(user);
                PostAllGamesInfo.postAllGamesToLobby();
            } catch (Exception e) {
                ServerLog.error(SCOPE, "CreateGame request failed.", e);
                sendJson(exchange, 400, errorJson("Bad create game request."));
            }
        }
    }

    static class LeaveGameHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JSONObject infoJson = getInfoJsonFromExchange(exchange);
                User user = requireUser(infoJson);

                Game game = GameHandler.getGame(user.getGameID());
                if (game == null) {
                    throw new IllegalArgumentException("Game not found.");
                }

                int gameId = game.getGameID();
                GameHandler.removeUserFromGame(user);
                if (game.getPlayers().isEmpty()) {
                    GameHandler.end(game);
                }
                ServerLog.info(
                    SCOPE,
                    String.format("User %s left game %d", user.getUsername(), gameId)
                );

                sendJson(exchange, 200, new JSONObject());
                PostAllGamesInfo.postAllGamesToLobby();
            } catch (Exception e) {
                ServerLog.error(SCOPE, "LeaveGame request failed.", e);
                sendJson(exchange, 400, errorJson("Bad leave game request."));
            }
        }
    }

    static class JoinGameHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JSONObject infoJson = getInfoJsonFromExchange(exchange);
                User user = requireUser(infoJson);
                Game requestedGame = GameHandler.getGame(infoJson.getInt("gameID"));
                if (requestedGame == null) {
                    throw new IllegalArgumentException("Game not found.");
                }
                if (requestedGame.getState() != State.WAITING) {
                    throw new IllegalStateException("Game is not joinable.");
                }
                if (requestedGame.getPlayers().size() == 5) {
                    throw new IllegalStateException("Game is full.");
                }
                if (user.hasForfeitedGame(requestedGame.getGameID())) {
                    throw new IllegalStateException("Cannot rejoin a forfeited game.");
                }

                if (user.getGameID() != -1) {
                    Game oldGame = GameHandler.getGame(user.getGameID());
                    GameHandler.removeUserFromGame(user);
                    if (oldGame != null && oldGame.getPlayers().isEmpty()) {
                        GameHandler.end(oldGame);
                    }
                }
                boolean joined = GameHandler.addUserToGame(user, requestedGame.getGameID());
                if (!joined) {
                    throw new IllegalStateException("Game join was rejected.");
                }
                ServerLog.info(
                    SCOPE,
                    String.format("User %s joined game %d", user.getUsername(), requestedGame.getGameID())
                );

                PostUserInfo.postUserInfo(user);
                PostAllGamesInfo.postAllGamesToLobby();
                sendJson(exchange, 200, new JSONObject());
            } catch (Exception e) {
                ServerLog.error(SCOPE, "JoinGame request failed.", e);
                sendJson(exchange, 400, errorJson("Bad join game request."));
            }
        }
    }

    static class StartGameHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JSONObject infoJson = getInfoJsonFromExchange(exchange);
                User requestingUser = requireUser(infoJson);

                Game game = GameHandler.getGame(requestingUser.getGameID());
                if (game == null) {
                    throw new IllegalArgumentException("Game not found.");
                }
                if (game.getState() != State.WAITING) {
                    throw new IllegalStateException("Game is not in waiting state.");
                }
                if (requestingUser.hasVoted()) {
                    throw new IllegalStateException("User already voted.");
                }

                requestingUser.setVoted();
                boolean everyoneVoted = GameHandler.everyoneVotedStart(game);
                if (everyoneVoted) {
                    ServerLog.info(
                        SCOPE,
                        String.format("All votes received for game %d. Starting game.", game.getGameID())
                    );
                    GameHandler.start(game);
                } else {
                    ServerLog.info(
                        SCOPE,
                        String.format("VoteStart recorded from %s for game %d", requestingUser.getUsername(), game.getGameID())
                    );
                }
                PostAllGamesInfo.postAllGamesToLobby();
                sendJson(exchange, 200, new JSONObject());
            } catch (Exception e) {
                ServerLog.error(SCOPE, "VoteStart request failed.", e);
                sendJson(exchange, 400, errorJson("Bad start game request."));
            }
        }
    }

    static class ListPlayers implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = User.getUsers();
            ServerLog.info(SCOPE, "Player list request served. payloadLength=" + response.length());
            send(exchange, 200, response);
        }
    }
}
