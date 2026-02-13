package HTTPHandlers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.time.Instant;

import org.json.JSONObject;

public class AWSSigner {
    private static final String RESTAPIHOST = "sbokdz62pc.execute-api.us-west-1.amazonaws.com";
    private static final String STAGE = "production";
    private static final String METHOD = "POST";
    private static final String SERVICE = "execute-api";
    private static final String REGION = "us-west-1";
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final int IMDS_TIMEOUT_MS = 1000;

    private static volatile AwsCredentials cachedCredentials;

    private static class AwsCredentials {
        final String accessKeyId;
        final String secretAccessKey;
        final String sessionToken;
        final long expiresAt;

        AwsCredentials(String accessKeyId, String secretAccessKey, String sessionToken, long expiresAt) {
            this.accessKeyId = accessKeyId;
            this.secretAccessKey = secretAccessKey;
            this.sessionToken = sessionToken;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            if (expiresAt <= 0) {
                return false;
            }
            return Instant.now().toEpochMilli() > (expiresAt - 60_000);
        }
    }

    public static void sendSignedMessage(String requestBody, ArrayList<String> connections) throws IOException, NoSuchAlgorithmException {
        AwsCredentials credentials = getCredentials();
        String restApiPath = "/" + STAGE + "/@connections/";
        String encodedRestApiPath = "/" + STAGE + "/%40connections/";

        // Create a datetime object for signing
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String amzDate = dateFormat.format(new Date());
        String dateStamp = amzDate.substring(0, 8);

        // Create the canonical request
        String canonicalQuerystring = "";
        String canonicalHeaders = "content-type:application/json\nhost:" + RESTAPIHOST + "\n";
        String signedHeaders = "content-type;host";
        if (credentials.sessionToken != null && !credentials.sessionToken.isBlank()) {
            canonicalHeaders += "x-amz-security-token:" + credentials.sessionToken + "\n";
            signedHeaders += ";x-amz-security-token";
        }
        String payloadHash = sha256Hex(requestBody);

        // Create the string to sign
        String credentialScope = dateStamp + "/" + REGION + "/" + SERVICE + "/" + "aws4_request";


        // Add signing information to the request
        for(int i = 0; i < connections.size(); i++) {

            String canonicalUri = restApiPath + connections.get(i);
            String encodedUri = encodedRestApiPath + URLEncoder.encode(connections.get(i), StandardCharsets.UTF_8);
            String canonicalRequest = METHOD + "\n" + encodedUri+ "\n" + canonicalQuerystring + "\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
            String hashedCanonicalRequest = sha256Hex(canonicalRequest);
            String stringToSign = ALGORITHM + "\n" + amzDate + "\n" + credentialScope + "\n" + hashedCanonicalRequest;

            // Sign the string
            byte[] signingKey = getSignatureKey(credentials.secretAccessKey, dateStamp, REGION, SERVICE);
            String signature = hmacSha256Hex(signingKey, stringToSign);
            String authorizationHeader = ALGORITHM + " " + "Credential=" + credentials.accessKeyId + "/" + credentialScope + ", " + "SignedHeaders=" + signedHeaders + ", " + "Signature=" + signature;
            // Make the header
            URL url = new URL("https://" + RESTAPIHOST + canonicalUri);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(METHOD);
            con.setRequestProperty("Host", RESTAPIHOST);
            con.setRequestProperty("x-amz-date", amzDate);
            con.setRequestProperty("Content-Type", "application/json");
            if (credentials.sessionToken != null && !credentials.sessionToken.isBlank()) {
                con.setRequestProperty("x-amz-security-token", credentials.sessionToken);
            }
            con.setRequestProperty("Authorization", authorizationHeader);
            //Make the body
            con.setDoOutput(true);

            // Write the request body
            try (var os = con.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            //Make request and Print the response
            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String responseBody = new String(con.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            } else {

                System.out.println("Error: " + responseCode + " " + con.getResponseMessage() );
                String errorBody = new String(con.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                System.out.println("Error body:"+errorBody);
            }
        }
    }

    private static byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) throws NoSuchAlgorithmException {
        byte[] kSecret = ("AWS4" + key).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, regionName);
        byte[] kService = hmacSha256(kRegion, serviceName);
        return hmacSha256(kService, "aws4_request");
    }

    private static String hmacSha256Hex(byte[] key, String data) throws NoSuchAlgorithmException {
        return bytesToHex(hmacSha256(key, data));
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error: HmacSHA256 algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException("Error: Invalid key for HmacSHA256", e);
        }
    }

    private static String sha256Hex(String data) throws NoSuchAlgorithmException {
        return bytesToHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private static AwsCredentials getCredentials() throws IOException {
        AwsCredentials cached = cachedCredentials;
        if (cached != null && !cached.isExpired()) {
            return cached;
        }

        AwsCredentials resolved = fetchInstanceProfileCredentials();
        cachedCredentials = resolved;
        return resolved;
    }

    private static AwsCredentials fetchInstanceProfileCredentials() throws IOException {
        String token = null;
        try {
            token = fetchImdsToken();
        } catch (IOException ignored) {
            token = null;
        }

        String roleName = fetchImds("http://169.254.169.254/latest/meta-data/iam/security-credentials/", token).trim();
        String credentialsJson = fetchImds(
            "http://169.254.169.254/latest/meta-data/iam/security-credentials/" + roleName,
            token
        );
        JSONObject json = new JSONObject(credentialsJson);
        String accessKeyId = json.getString("AccessKeyId");
        String secretAccessKey = json.getString("SecretAccessKey");
        String sessionToken = json.optString("Token", "");
        String expiration = json.optString("Expiration", "");
        long expiresAt = 0;
        if (!expiration.isBlank()) {
            expiresAt = Instant.parse(expiration).toEpochMilli();
        }
        return new AwsCredentials(accessKeyId, secretAccessKey, sessionToken, expiresAt);
    }

    private static String fetchImdsToken() throws IOException {
        URL url = new URL("http://169.254.169.254/latest/api/token");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(IMDS_TIMEOUT_MS);
        con.setReadTimeout(IMDS_TIMEOUT_MS);
        con.setRequestMethod("PUT");
        con.setRequestProperty("X-aws-ec2-metadata-token-ttl-seconds", "21600");
        con.setDoOutput(true);
        con.getOutputStream().write(new byte[0]);
        return new String(con.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    private static String fetchImds(String url, String token) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(IMDS_TIMEOUT_MS);
        con.setReadTimeout(IMDS_TIMEOUT_MS);
        con.setRequestMethod("GET");
        if (token != null && !token.isBlank()) {
            con.setRequestProperty("X-aws-ec2-metadata-token", token);
        }
        return new String(con.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
