package GameHandlers;

import HTTPHandlers.PostAllGamesInfo;
import HTTPHandlers.PostAllUsersToLobby;
import HTTPHandlers.PostGameState;
import HTTPHandlers.PostUserInfo;
import HTTPHandlers.ServerLog;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

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

    public static void forfeitUser(User user) {
        if (user == null) {
            return;
        }
        if (user.getGameID() != -1) {
            user.markGameForfeited(user.getGameID());
        }
        if (user.getGameID() == -1) {
            if (cleanupOrphanedMemberships()) {
                PostAllGamesInfo.postAllGamesToLobby();
            }
            return;
        }
        Game game = getGame(user.getGameID());
        if (game == null) {
            user.setGameID(-1);
            user.setState(State.LOBBY);
            user.unsetVote();
            PostUserInfo.postUserInfo(user);
            PostAllUsersToLobby.postAllUsersToLobby();
            if (cleanupOrphanedMemberships()) {
                PostAllGamesInfo.postAllGamesToLobby();
            }
            return;
        }

        int gameId = game.getGameID();
        if (game.getState() == State.INGAME) {
            boolean replaced = game.replaceUserWithBot(user);
            user.setGameID(-1);
            user.setState(State.LOBBY);
            user.unsetVote();
            PostUserInfo.postUserInfo(user);
            PostAllUsersToLobby.postAllUsersToLobby();
            if (!game.hasHumanPlayers()) {
                ServerLog.info(
                    SCOPE,
                    "All players forfeited for game " + gameId + ". Ending immediately."
                );
                end(game);
                if (cleanupOrphanedMemberships()) {
                    PostAllGamesInfo.postAllGamesToLobby();
                }
                return;
            }
            if (replaced) {
                game.updateBotOnlyTimer();
                Game active = getGame(gameId);
                if (active != null && active.getState() == State.INGAME) {
                    active.refreshTurnTimer();
                    PostGameState.postGameState(active);
                }
            }
            cleanupOrphanedMemberships();
            PostAllGamesInfo.postAllGamesToLobby();
            return;
        }

        detachPlayerFromGame(game, user);
        if (game.getPlayers().isEmpty()) {
            end(game);
        } else {
            cleanupOrphanedMemberships();
            PostAllGamesInfo.postAllGamesToLobby();
        }
        PostAllUsersToLobby.postAllUsersToLobby();
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
        boolean doHistoryStore = !game.getState().equals(State.WAITING);
        endInternal(game, doHistoryStore, true);
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
            ServerLog.info(
                SCOPE,
                "Explicit in-game remove for " + u.getUsername() + " treated as forfeit."
            );
            forfeitUser((User) u);
            return;
        }
        detachPlayerFromGame(game, u);
        if (game.getPlayers().isEmpty()) {
            end(game);
            return;
        }
        cleanupOrphanedMemberships();
        PostAllGamesInfo.postAllGamesToLobby();
        if (u instanceof User) {
            PostAllUsersToLobby.postAllUsersToLobby();
        }
    }

    private static void detachPlayerFromGame(Game game, Player u) {
        if (game == null || u == null) {
            return;
        }
        game.removePlayer(u);
        if (u instanceof User) {
            User user = (User) u;
            if (user.getGameID() == game.getGameID()) {
                user.setGameID(-1);
                user.setState(State.LOBBY);
                user.unsetVote();
                ServerLog.info(SCOPE, user.getUsername() + " left game " + game.getGameID());
                PostUserInfo.postUserInfo(user);
            } else {
                ServerLog.warn(
                    SCOPE,
                    "Removed stale membership for " + user.getUsername()
                        + " from game " + game.getGameID()
                        + " without mutating active gameID " + user.getGameID()
                );
            }
            return;
        }
        u.setGameID(-1);
        u.setState(State.LOBBY);
        u.unsetVote();
        ServerLog.info(SCOPE, u.getUsername() + " left game " + game.getGameID());
    }

    public static boolean addUserToGame(User u, int gameID) {
        Game game = getGame(gameID);
        if (game == null || u == null) {
            return false;
        }
        if (u.hasForfeitedGame(gameID)) {
            ServerLog.warn(
                SCOPE,
                "Blocked " + u.getUsername() + " from rejoining forfeited game " + gameID
            );
            return false;
        }
        if (game.getPlayers().contains(u)) {
            u.setGameID(gameID);
            u.setState(State.WAITING);
            return true;
        }
        game.addPlayer(u);
        u.setState(State.WAITING);
        cleanupOrphanedMemberships();
        ServerLog.info(SCOPE, u.getUsername() + " added to game " + game.getGameID());
        return true;
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
        cleanupOrphanedMemberships();
        JSONObject lobby = new JSONObject();
        for (Game game : new ArrayList<>(games.values())) {
            ArrayList<Player> players = getRenderablePlayers(game);
            if (players.isEmpty()) {
                continue;
            }
            JSONObject gameJson = new JSONObject();
            String host = players.get(0).getUsername();
            gameJson.put("host", host);
            for (int i = 0; i < 5; i++) {
                boolean hasPlayer = i < players.size();
                Player player = hasPlayer ? players.get(i) : null;
                boolean voted = hasPlayer && player.hasVoted();
                if (!hasPlayer) {
                    gameJson.put("player" + (i + 1), "Empty");
                } else {
                    JSONObject playerJson = new JSONObject();
                    playerJson.put("name", player.getUsername());
                    playerJson.put("isBot", player instanceof Bot);
                    if (player instanceof User) {
                        User user = (User) player;
                        playerJson.put("isOffline", !user.isOnline());
                        playerJson.put("cardBack", user.getCardBack());
                        playerJson.put("cardFront", user.getCardFront());
                    } else if (player instanceof Bot) {
                        Bot bot = (Bot) player;
                        playerJson.put("isOffline", false);
                        playerJson.put("cardBack", bot.getCardBack());
                        playerJson.put("cardFront", bot.getCardFront());
                    }
                    gameJson.put("player" + (i + 1), playerJson);
                }
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
        boolean cleaned = cleanupOrphanedMemberships();
        if (user.getGameID() == -1) {
            user.setState(State.LOBBY);
            if (cleaned) {
                PostAllGamesInfo.postAllGamesToLobby();
            }
            return;
        }
        if (user.hasForfeitedGame(user.getGameID())) {
            ServerLog.info(
                SCOPE,
                "User " + user.getUsername()
                    + " attempted reconnect to forfeited game " + user.getGameID()
                    + ". Forcing lobby state."
            );
            forfeitUser(user);
            if (cleaned) {
                PostAllGamesInfo.postAllGamesToLobby();
            }
            return;
        }
        Game game = getGame(user.getGameID());
        if (game == null) {
            user.setState(State.LOBBY);
            user.setGameID(-1);
            if (cleaned) {
                PostAllGamesInfo.postAllGamesToLobby();
            }
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
        if (cleaned) {
            PostAllGamesInfo.postAllGamesToLobby();
        }
    }

    public static void handleUserDisconnected(User user) {
        if (user == null || user.getGameID() == -1) {
            if (cleanupOrphanedMemberships()) {
                PostAllGamesInfo.postAllGamesToLobby();
            }
            return;
        }
        Game game = getGame(user.getGameID());
        if (game == null) {
            if (cleanupOrphanedMemberships()) {
                PostAllGamesInfo.postAllGamesToLobby();
            }
            return;
        }
        if (game.getState() == State.WAITING && !game.hasOnlineHumanPlayers()) {
            ServerLog.info(
                SCOPE,
                "No online players remain in waiting game " + game.getGameID() + ". Ending immediately."
            );
            end(game);
            if (cleanupOrphanedMemberships()) {
                PostAllGamesInfo.postAllGamesToLobby();
            }
            return;
        }
        if (!game.hasHumanPlayers()) {
            ServerLog.info(
                SCOPE,
                "Game " + game.getGameID() + " has no human players. Ending immediately."
            );
            end(game);
            if (cleanupOrphanedMemberships()) {
                PostAllGamesInfo.postAllGamesToLobby();
            }
            return;
        }
        game.updateBotOnlyTimer();
        game.refreshTurnTimer();
        PostGameState.postGameState(game);
        if (cleanupOrphanedMemberships()) {
            PostAllGamesInfo.postAllGamesToLobby();
        }
    }

    private static void endInternal(Game game, boolean doHistoryStore, boolean postLobby) {
        if (game == null || getGame(game.getGameID()) == null) {
            return;
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
        if (postLobby) {
            PostAllGamesInfo.postAllGamesToLobby();
        }
    }

    private static boolean cleanupOrphanedMemberships() {
        boolean changed = false;
        ArrayList<Game> snapshot = new ArrayList<>(games.values());
        ArrayList<Game> gamesToEnd = new ArrayList<>();
        Set<Integer> gamesToRefresh = new LinkedHashSet<>();

        for (Game game : snapshot) {
            if (game == null || getGame(game.getGameID()) == null) {
                continue;
            }
            ArrayList<Player> playerSnapshot = new ArrayList<>(game.getPlayers());
            boolean gameChanged = false;

            for (Player player : playerSnapshot) {
                if (!(player instanceof User)) {
                    continue;
                }
                User user = (User) player;
                if (user.getGameID() == game.getGameID()) {
                    continue;
                }
                ServerLog.warn(
                    SCOPE,
                    "Detected stale membership for " + user.getUsername()
                        + " in game " + game.getGameID()
                        + " while active gameID is " + user.getGameID()
                );

                if (game.getState() == State.INGAME) {
                    user.markGameForfeited(game.getGameID());
                    boolean replaced = game.replaceUserWithBot(user);
                    if (!replaced) {
                        game.removePlayer(user);
                    }
                    game.updateBotOnlyTimer();
                    game.refreshTurnTimer();
                    PostUserInfo.postUserInfo(user);
                    gamesToRefresh.add(game.getGameID());
                } else {
                    game.removePlayer(user);
                }
                changed = true;
                gameChanged = true;
            }

            if (game.getPlayers().isEmpty()) {
                gamesToEnd.add(game);
                continue;
            }
            if (game.getState() == State.INGAME && !game.hasHumanPlayers()) {
                gamesToEnd.add(game);
                continue;
            }
            if (game.getState() == State.WAITING && !game.hasOnlineHumanPlayers()) {
                gamesToEnd.add(game);
                continue;
            }
            if (gameChanged && game.getState() == State.INGAME) {
                gamesToRefresh.add(game.getGameID());
            }
        }

        for (Game game : gamesToEnd) {
            if (game == null || getGame(game.getGameID()) == null) {
                continue;
            }
            ServerLog.info(
                SCOPE,
                "Cleaning up abandoned game " + game.getGameID() + " during membership reconciliation."
            );
            boolean doHistoryStore = !game.getState().equals(State.WAITING);
            endInternal(game, doHistoryStore, false);
            changed = true;
            gamesToRefresh.remove(game.getGameID());
        }

        for (Integer gameId : gamesToRefresh) {
            Game active = getGame(gameId);
            if (active != null && active.getState() == State.INGAME) {
                PostGameState.postGameState(active);
            }
        }

        return changed;
    }

    private static ArrayList<Player> getRenderablePlayers(Game game) {
        ArrayList<Player> players = new ArrayList<>();
        if (game == null) {
            return players;
        }
        for (Player player : game.getPlayers()) {
            if (player instanceof User) {
                User user = (User) player;
                if (user.getGameID() != game.getGameID()) {
                    continue;
                }
            }
            players.add(player);
        }
        return players;
    }
}
