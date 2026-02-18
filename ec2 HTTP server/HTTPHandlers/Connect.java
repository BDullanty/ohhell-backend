package HTTPHandlers;

import GameHandlers.GameHandler;
import GameHandlers.User;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Connect {
    private static final String SCOPE = "Connect";

    private static final ScheduledExecutorService CONNECT_EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private static final long POST_CONNECT_DELAY_MS = 250;

    //Expected String Input:
    //{"action":"something","jwk":"something", "connectionID":"something"}
    public static String connectPlayer(HttpExchange exchange) throws IOException {
        String response;
        try {
            //parse exhange into json with info.
            JSONObject infoJson = ExchangeHandler.getInfoJsonFromExchange(exchange);
            User connectingPlayer = getUserFromParsedJWK(infoJson);
            if (connectingPlayer == null) {
                throw new IllegalArgumentException("Unable to resolve connecting user.");
            }
            //If  our jwk does process into a player and sub,
            User.addUserOnline(connectingPlayer);
            ServerLog.info(SCOPE, "Player connected: " + connectingPlayer.getUsername());
            //And return our response
            response = "{\"player\": \"" + connectingPlayer.getUsername() + "\"}";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
            schedulePostConnect(connectingPlayer);

        } catch (Exception e) {
            //If we failed to get a player properly, return 400
            ServerLog.error(SCOPE, "Bad connect request.", e);
            response = "{\"error\":\"Bad Connect Request\"}";
            exchange.sendResponseHeaders(400, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
        return response;
    }

    private static void schedulePostConnect(User user) {
        if (user == null) {
            return;
        }
        CONNECT_EXECUTOR.schedule(() -> {
            try {
                if (!user.isOnline()) {
                    return;
                }
                GameHandler.handleUserConnected(user);
                //Send this user some info about themselves:
                PostUserInfo.postUserInfo(user);
                //Show this user the lobbies:
                PostAllGamesInfo.postAllGamesToUser(user);
                //Notify everyone that someone connected:
                PostAllUsersToLobby.postAllUsersToLobby();
            } catch (Exception e) {
                ServerLog.error(SCOPE, "Post-connect actions failed.", e);
            }
        }, POST_CONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    public static User getUserFromParsedJWK(JSONObject infoJson) {
        try {
            return User.getUser(
                infoJson.getString("sub"),
                infoJson.getString("username"),
                infoJson.getString("connectionID")
            );
        } catch (Exception e) {
            ServerLog.error(SCOPE, "Failed creating user from token payload.", e);
            return null;
        }


    }
}
