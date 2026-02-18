package GameHandlers;

import HTTPHandlers.PostAllGamesInfo;
import HTTPHandlers.PostGameState;
import HTTPHandlers.PostUserInfo;
import HTTPHandlers.ServerLog;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

public class GameHandler {
    private static final String SCOPE = "GameHandler";
    private static final HashMap<Integer, Game> games = new HashMap<>();
    private static final HashMap<Integer, Game> history = new HashMap<>();

    public static Game getGame(int gameID) {
        return games.get(gameID);
    }

    public static void start(Game game) {
        if (game == null) {
            return;
        }
        ServerLog.info(SCOPE, "Starting game " + game.getGameID());
        game.startGame();
        PostAllGamesInfo.postAllGamesToLobby();
    }

    public static void handleBet(User user, int bet) {
        if (user == null || user.getGameID() == -1) {
            return;
        }
        Game game = getGame(user.getGameID());
        if (game == null) {
            return;
        }
        game.handleBet(user, bet);
    }

    public static void handlePlay(User user, String cardKey) {
        if (user == null || user.getGameID() == -1) {
            return;
        }
        Game game = getGame(user.getGameID());
        if (game == null) {
            return;
        }
        game.handlePlay(user, cardKey);
    }

    public static void addGameToLobby(Game game) {
        ServerLog.info(SCOPE, "Adding game " + game.getGameID() + " to lobby");
        games.put(game.getGameID(), game);
        game.setState(State.WAITING);
    }

    public static void end(Game game) {
        if (game == null) {
            return;
        }
        ServerLog.info(SCOPE, "Ending game " + game.getGameID());
        boolean doHistoryStore = true;
        if (game.getState().equals(State.WAITING)) {
            doHistoryStore = false;
        }
        game.setState(State.COMPLETED);
        ArrayList<Player> snapshot = new ArrayList<>(game.getPlayers());
        for (Player player : snapshot) {
            detachPlayerFromGame(game, player);
        }
        games.remove(game.getGameID());
        if (doHistoryStore) {
            history.put(game.getGameID(), game);
        }
        game.shutdown();
        PostAllGamesInfo.postAllGamesToLobby();
    }

    public static void removeUserFromGame(Player u) {
        if (u == null || u.getGameID() == -1) {
            return;
        }
        Game game = getGame(u.getGameID());
        if (game == null) {
            return;
        }
        if (game.getState() == State.INGAME && u instanceof User) {
            u.setState(State.OFFLINE);
            game.updateBotOnlyTimer();
            game.refreshTurnTimer();
            PostGameState.postGameState(game);
            return;
        }
        detachPlayerFromGame(game, u);
        PostAllGamesInfo.postAllGamesToLobby();
    }

    private static void detachPlayerFromGame(Game game, Player u) {
        if (game == null || u == null) {
            return;
        }
        game.removePlayer(u);
        u.setGameID(-1);
        u.setState(State.LOBBY);
        u.unsetVote();
        ServerLog.info(SCOPE, u.getUsername() + " left game " + game.getGameID());
        if (u instanceof User) {
            PostUserInfo.postUserInfo((User) u);
        }
    }

    public static void addUserToGame(User u, int gameID) {
        Game game = getGame(gameID);
        if (game == null || u == null) {
            return;
        }
        game.addPlayer(u);
        u.setState(State.WAITING);
        ServerLog.info(SCOPE, u.getUsername() + " added to game " + game.getGameID());
    }

    public static boolean everyoneVotedStart(Game game) {
        if (game == null) {
            return false;
        }
        boolean ready = true;
        for (Player p : game.getPlayers()) {
            if (!p.hasVoted()) {
                ServerLog.info(
                    SCOPE,
                    "Still waiting for vote from " + p.getUsername() + " in game " + game.getGameID()
                );
                ready = false;
            } else {
                ServerLog.info(SCOPE, p.getUsername() + " has voted");
            }
        }
        return ready;
    }

    public static JSONObject getLobbyGames() {
        JSONObject lobby = new JSONObject();
        for (Game game : games.values()) {
            ArrayList<Player> players = game.getPlayers();
            JSONObject gameJson = new JSONObject();
            String host = players.isEmpty() ? "Empty" : players.get(0).getUsername();
            gameJson.put("host", host);
            for (int i = 0; i < 5; i++) {
                boolean hasPlayer = i < players.size();
                String playerName = hasPlayer ? players.get(i).getUsername() : "Empty";
                boolean voted = hasPlayer && players.get(i).hasVoted();
                gameJson.put("player" + (i + 1), playerName);
                gameJson.put("player" + (i + 1) + "Voted", voted);
            }
            gameJson.put("hostVoted", !players.isEmpty() && players.get(0).hasVoted());
            gameJson.put("state", game.getState().toString());
            gameJson.put("round", game.getRound());
            lobby.put(String.valueOf(game.getGameID()), gameJson);
        }
        return lobby;
    }

    public static String getLobbyGamesJson() {
        return getLobbyGames().toString();
    }

    public static void handleUserConnected(User user) {
        if (user == null) {
            return;
        }
        if (user.getGameID() == -1) {
            user.setState(State.LOBBY);
            return;
        }
        Game game = getGame(user.getGameID());
        if (game == null) {
            user.setState(State.LOBBY);
            user.setGameID(-1);
            return;
        }
        if (game.getState() == State.WAITING) {
            user.setState(State.WAITING);
        } else {
            user.setState(State.INGAME);
        }
        game.updateBotOnlyTimer();
        game.refreshTurnTimer();
        PostGameState.postGameState(game);
    }

    public static void handleUserDisconnected(User user) {
        if (user == null || user.getGameID() == -1) {
            return;
        }
        Game game = getGame(user.getGameID());
        if (game == null) {
            return;
        }
        game.updateBotOnlyTimer();
        game.refreshTurnTimer();
        PostGameState.postGameState(game);
    }
}
