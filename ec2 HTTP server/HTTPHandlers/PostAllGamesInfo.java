package HTTPHandlers;

import GameHandlers.GameHandler;
import GameHandlers.User;
import org.json.JSONObject;

import java.util.ArrayList;

public class PostAllGamesInfo {
    private static final String SCOPE = "PostAllGamesInfo";

    public static void postAllGamesToLobby() {
        try {
            ArrayList<String> lobbyConnections = User.getLobbyConnections();
            JSONObject payload = buildPayload();

            AWSSigner.sendSignedMessage(payload.toString(), lobbyConnections);
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < lobbyConnections.size(); i++) {
                User user = User.getUser(lobbyConnections.get(i));
                if (user != null) {
                    names.append(user.getUsername()).append(' ');
                }
            }
            ServerLog.info(
                SCOPE,
                "Posted games payload to " + lobbyConnections.size() + " lobby connections: " + names
            );
        } catch (Exception e) {
            ServerLog.error(SCOPE, "Failed to post games payload to lobby.", e);

        }


    }

    public static void postAllGamesToUser(User user) {
        try {
            ArrayList<String> lobbyConnections = user.getConnections();
            JSONObject payload = buildPayload();

            AWSSigner.sendSignedMessage(payload.toString(), lobbyConnections);
            ServerLog.info(
                SCOPE,
                "Posted games payload directly to user " + user.getUsername() + " (" + lobbyConnections.size() + " connections)"
            );
        } catch (Exception e) {
            ServerLog.error(SCOPE, "Failed posting games payload to user.", e);

        }

    }

    private static JSONObject buildPayload() {
        JSONObject payload = new JSONObject();
        payload.put("returnType", "gameList");
        payload.put("games", GameHandler.getLobbyGames());
        return payload;
    }
}
