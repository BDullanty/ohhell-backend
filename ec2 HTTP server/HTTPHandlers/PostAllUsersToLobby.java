package HTTPHandlers;

import GameHandlers.User;
import org.json.JSONObject;

import java.util.ArrayList;

public class PostAllUsersToLobby {
    private static final String SCOPE = "PostAllUsersToLobby";
    
    public static void postAllUsersToLobby(){
        ArrayList<String> lobbyConnections = User.getLobbyConnections();
        JSONObject payload = new JSONObject();
        payload.put("returnType", "users");
        payload.put("users", User.getUsersObject());
        try {
            AWSSigner.sendSignedMessage(payload.toString(),lobbyConnections);
            ServerLog.info(
                SCOPE,
                "Sent users payload to " + lobbyConnections.size() + " lobby connections"
            );
        } catch (Exception e) {
            ServerLog.error(SCOPE, "Failed posting users payload.", e);

        }
    }
    
}
