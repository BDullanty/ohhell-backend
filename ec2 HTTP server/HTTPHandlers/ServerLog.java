package HTTPHandlers;

import java.time.Instant;

public final class ServerLog {
    private ServerLog() {}

    public static void info(String scope, String message) {
        log("INFO", scope, message, null);
    }

    public static void warn(String scope, String message) {
        log("WARN", scope, message, null);
    }

    public static void error(String scope, String message, Throwable error) {
        log("ERROR", scope, message, error);
    }

    private static void log(String level, String scope, String message, Throwable error) {
        String line = String.format("%s [%s] [%s] %s", Instant.now(), level, scope, message);
        if ("ERROR".equals(level)) {
            System.err.println(line);
            if (error != null) {
                error.printStackTrace(System.err);
            }
            return;
        }
        System.out.println(line);
    }
}
