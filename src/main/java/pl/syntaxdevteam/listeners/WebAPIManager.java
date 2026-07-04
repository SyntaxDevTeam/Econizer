package pl.syntaxdevteam.listeners;

import io.github.cdimascio.dotenv.Dotenv;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

public class WebAPIManager {

    public static final String BASE_URL = "https://econizer.syntaxdevteam.pl";
    private static final String API_TOKEN = loadApiToken();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static void reportGuildAction(String guildId, String guildName, String action) {
        String url = BASE_URL + "/api/econizer/guilds";
        String jsonPayload = String.format(
                "{\"guild_id\":\"%s\",\"name\":\"%s\",\"action\":\"%s\"}",
                escapeJson(guildId), escapeJson(guildName), escapeJson(action)
        );
        sendPostRequest(url, jsonPayload, "guild " + action + " " + guildId);
    }

    public static void reportEconomyEvent(String guildId, String userId, String type, int amount, int exp, int level, String description) {
        String url = BASE_URL + "/api/econizer/events";
        String eventId = UUID.randomUUID().toString();

        String jsonPayload = String.format(
                "{\"event_id\":\"%s\",\"guild_id\":\"%s\",\"user_id\":\"%s\",\"type\":\"%s\",\"amount\":%d,\"experience\":%d,\"level\":%d,\"description\":\"%s\"}",
                escapeJson(eventId), escapeJson(guildId), escapeJson(userId), escapeJson(type), amount, exp, level, escapeJson(description)
        );
        sendPostRequest(url, jsonPayload, "economy event " + eventId);
    }

    public static String getApiToken() {
        return API_TOKEN;
    }

    private static void sendPostRequest(String url, String jsonPayload, String context) {
        if (API_TOKEN == null) {
            System.err.println("[WebAPI Błąd] Brak ECONIZER_API_TOKEN - pomijam synchronizację: " + context);
            return;
        }

        Thread.startVirtualThread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("X-Econizer-Token", API_TOKEN)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    System.err.println("[WebAPI Błąd] Nieudana synchronizacja " + context + ". HTTP " + response.statusCode());
                }
            } catch (Exception e) {
                System.err.println("[WebAPI Błąd] Nie udało się połączyć ze stroną WWW dla: " + context);
            }
        });
    }

    private static String loadApiToken() {
        String token = System.getenv("ECONIZER_API_TOKEN");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        token = dotenv.get("ECONIZER_API_TOKEN");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        return null;
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
