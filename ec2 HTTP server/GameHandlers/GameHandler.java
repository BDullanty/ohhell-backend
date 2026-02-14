package GameHandlers;

import HTTPHandlers.PostAllGamesInfo;
import HTTPHandlers.PostGameState;
import HTTPHandlers.PostUserInfo;

import java.util.ArrayList;
import java.util.HashMap;

public class GameHandler {
    private static final HashMap<Integer, Game> games = new HashMap<>();
    private static final HashMap<Integer, Game> history = new HashMap<>();

    public static Game getGame(int gameID) {
        Game resultGame = games.get(gameID);
        if (resultGame == null) {
            return null;
        }
        System.out.println("Result from getGame: passed id: " + gameID + " resultantID: " + resultGame.getGameID());
        return resultGame;
    }

    public static void start(Game game) {
        if (game == null) {
            return;
        }
        System.out.println("Starting Game");
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
        System.out.println("Moving game to lobby: " + game.getGameID());
        games.put(game.getGameID(), game);
        game.setState(State.WAITING);
    }

    public static void end(Game game) {
        if (game == null) {
            return;
        }
        System.out.println("Ending game " + game.getGameID());
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
        System.out.println(u.getUsername() + " left game " + game.getGameID());
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
        System.out.print(u.getUsername() + " added to game " + game.getGameID());
    }

    public static boolean everyoneVotedStart(Game game) {
        boolean ready = true;
        for (Player p : game.getPlayers()) {
            if (!p.hasVoted()) {
                System.out.println("Still waiting for vote from " + p.getUsername() + " in game " + game.getGameID());
                ready = false;
            } else {
                System.out.println(p.getUsername() + " has voted");
            }
        }
        return ready;
    }

    public static String getLobbyGamesJson() {
        System.out.println("There are " + games.size() + " lobbies");
        String gameString = "{";
        int tracker = 0;
        for (Game game : games.values()) {
            tracker += 1;
            ArrayList<Player> players = game.getPlayers();
            gameString += " \"" + game.getGameID() + "\" : {";
            gameString += "\"host\":\"" + players.get(0).username + "\",";
            int size = players.size();
            for (int j = 0; j < size; j++) {
                gameString += "\"player" + (j + 1) + "\":\"" + players.get(j).username + "\", ";
            }
            for (int k = 1; k <= 5 - size; k++) {
                gameString += "\"player" + (k + size) + "\":\"Empty\", ";
            }
            gameString += "\"state\":\"" + game.getState() + "\",";
            gameString += "\"round\":\"" + game.getRound() + "\"";
            if (tracker != games.size()) {
                gameString += "},";
            } else {
                gameString += "}";
            }
        }
        gameString += "}";
        return gameString;
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
