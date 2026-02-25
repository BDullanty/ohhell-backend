package HTTPHandlers;

import GameHandlers.User;
import org.json.JSONObject;

public class PostUserInfo {
    private static final String SCOPE = "PostUserInfo";

    public static void postUserInfo(User user){
            try{

                JSONObject returnJson = new JSONObject();
                returnJson.put("returnType","userInfo");
                String username = user.getUsername();
                String state = user.getState().toString();
                returnJson.put("username", username);
                returnJson.put("state", state);
                returnJson.put("cardBack", user.getCardBack());
                returnJson.put("cardFront", user.getCardFront());
                returnJson.put("gameID", user.getGameID());
                int lastForfeitedGameID = user.getLastForfeitedGameID();
                returnJson.put(
                    "lastForfeitedGameID",
                    lastForfeitedGameID >= 0 ? lastForfeitedGameID : JSONObject.NULL
                );
                returnJson.put("forfeitedGameIDs", user.getForfeitedGameIDs());
                String message = returnJson.toString();
                AWSSigner.sendSignedMessage(message,user.getConnections());
                ServerLog.info(SCOPE, "Posted user info for " + user.getUsername());
            } catch (Exception e){
                ServerLog.error(SCOPE, "Failed to post user info.", e);
            }
    }
}
