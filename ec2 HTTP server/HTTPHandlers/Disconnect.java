package HTTPHandlers;

import GameHandlers.User;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;

import java.io.IOException;

public class Disconnect {
    private static final String SCOPE = "Disconnect";


    //Expected String Input:
    //{"connectionID":"something"}
    public static void disconnectPlayer(HttpExchange exchange)throws IOException {
        try{
            JSONObject infoJson = ExchangeHandler.getInfoJsonFromExchange(exchange);
            User user = User.removeConnection(infoJson.getString("connectionID"));
            if (user == null) {
                ServerLog.warn(SCOPE, "Disconnect received for unknown connection.");
                return;
            }
            ServerLog.info(SCOPE, "Disconnected user " + user.getUsername());
            GameHandlers.GameHandler.handleUserDisconnected(user);
        }catch (Exception e){
            ServerLog.error(SCOPE, "Disconnect handling failed.", e);
        }
    }
}
