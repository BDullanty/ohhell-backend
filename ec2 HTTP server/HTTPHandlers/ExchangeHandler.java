package HTTPHandlers;

import GameHandlers.User;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class ExchangeHandler {
    public static JSONObject getInfoJsonFromExchange(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes());
        JSONObject jsonObject = new JSONObject(body);
        if (jsonObject.has("jwk")) {
            String jwk = jsonObject.getString("jwk");
            String[] subAndName = decodeJWK(jwk);

            jsonObject.put("sub", subAndName[0]);
            jsonObject.put("username", subAndName[1]);
            jsonObject.remove("jwk");
        } else if (jsonObject.has("connectionID")) {
            User user = User.getUser(jsonObject.getString("connectionID"));
            if (user != null) {
                jsonObject.put("username", user.getUsername());
                jsonObject.put("sub", user.getSub());
            } else {
                ServerLog.warn("ExchangeHandler", "Unknown connectionID in exchange payload.");
            }
        }

        return jsonObject;
    }

    private static String[] decodeJWK(String jwk) {
        String[] splitJWK = jwk.split("\\.");
        if (splitJWK.length < 2) {
            throw new IllegalArgumentException("Malformed JWT payload.");
        }
        String payload = decodeBase64Url(splitJWK[1]);
        return getValuesFromDecodedJWK(payload);
    }

    private static String decodeBase64Url(String value) {
        String normalized = value.replace('-', '+').replace('_', '/');
        int remainder = normalized.length() % 4;
        if (remainder != 0) {
            normalized += "=".repeat(4 - remainder);
        }
        Base64.Decoder decoder = Base64.getDecoder();
        return new String(decoder.decode(normalized), StandardCharsets.UTF_8);
    }

    private static String[] getValuesFromDecodedJWK(String payload) {
        JSONObject jsonObject = new JSONObject(payload);
        String[] returnValues = new String[2];
        returnValues[0] = jsonObject.getString("sub");
        returnValues[1] = jsonObject.getString("username");
        return returnValues;
    }
}

