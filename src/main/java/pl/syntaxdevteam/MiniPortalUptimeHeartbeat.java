package pl.syntaxdevteam;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MiniPortalUptimeHeartbeat {

    private static final String DEFAULT_EVENT = "online";
    private static final long DEFAULT_INTERVAL_SECONDS = 60L;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final URI endpoint;
    private final String uuid;
    private final String event;

    private MiniPortalUptimeHeartbeat(String endpoint, String uuid, String event) {
        this.endpoint = URI.create(endpoint);
        this.uuid = uuid;
        this.event = event;
    }

    public static ScheduledExecutorService startFromConfig() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        String endpoint = readConfig(dotenv, "MINIPORTAL_UPTIME_ENDPOINT");
        String uuid = readConfig(dotenv, "MINIPORTAL_UPTIME_UUID");
        String event = readConfig(dotenv, "MINIPORTAL_UPTIME_EVENT", DEFAULT_EVENT);
        long intervalSeconds = readPositiveLongConfig(
                dotenv,
                "MINIPORTAL_UPTIME_INTERVAL_SECONDS",
                DEFAULT_INTERVAL_SECONDS
        );

        if (endpoint == null || uuid == null) {
            System.err.println("[Uptime] Brak MINIPORTAL_UPTIME_ENDPOINT lub MINIPORTAL_UPTIME_UUID - heartbeat wyłączony.");
            return null;
        }

        try {
            MiniPortalUptimeHeartbeat heartbeat = new MiniPortalUptimeHeartbeat(endpoint, uuid, event);
            return heartbeat.start(intervalSeconds);
        } catch (IllegalArgumentException exception) {
            System.err.println("[Uptime] Niepoprawny MINIPORTAL_UPTIME_ENDPOINT - heartbeat wyłączony.");
            return null;
        }
    }

    private ScheduledExecutorService start(long intervalSeconds) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "miniportal-uptime-heartbeat");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleAtFixedRate(
                () -> send("Online"),
                0,
                intervalSeconds,
                TimeUnit.SECONDS
        );
        System.out.println("[Uptime] Heartbeat miniPORTAL uruchomiony co " + intervalSeconds + "s.");
        return scheduler;
    }

    private void send(String message) {
        String payload = "{\"uuid\":\"" + escapeJson(uuid)
                + "\",\"event\":\"" + escapeJson(event)
                + "\",\"message\":\"" + escapeJson(message) + "\"}";

        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .header("X-Uptime-UUID", uuid)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("[Uptime] Heartbeat miniPORTAL odrzucony. HTTP " + response.statusCode());
            }
        } catch (IOException exception) {
            System.err.println("[Uptime] Błąd połączenia heartbeat miniPORTAL: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IllegalArgumentException exception) {
            System.err.println("[Uptime] Nie udało się przygotować żądania heartbeat: " + exception.getMessage());
        }
    }

    private static String readConfig(Dotenv dotenv, String key) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }

        value = dotenv.get(key);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }

        return null;
    }

    private static String readConfig(Dotenv dotenv, String key, String defaultValue) {
        String value = readConfig(dotenv, key);
        return value == null ? defaultValue : value;
    }

    private static long readPositiveLongConfig(Dotenv dotenv, String key, long defaultValue) {
        String value = readConfig(dotenv, key);
        if (value == null) {
            return defaultValue;
        }

        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }

        System.err.println("[Uptime] Niepoprawna wartość " + key + " - używam " + defaultValue + "s.");
        return defaultValue;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
